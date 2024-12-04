package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.ConcreteFunction;
import org.tensorflow.DeviceSpec;
import org.tensorflow.Graph;
import org.tensorflow.Operand;
import org.tensorflow.Session;
import org.tensorflow.Signature;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.IntNdArray;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.ndarray.index.Index;
import org.tensorflow.ndarray.index.Indices;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.For;
import org.tensorflow.op.core.If;
import org.tensorflow.op.core.Stack;
import org.tensorflow.op.nn.MaxPool3d;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

public class TFMaxFilterAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(TFMaxFilterAlgorithm.class);

    /**
     * Perform a max filter operation on a 2D image using a rectangular kernel. If any of the radii is 0, the kernel will
     * not be applied along that axis - so for example to perform a max filter only along the x-axis only, set yradius and zradius to 0.
     *
     * @param input
     * @param xradius
     * @param yradius
     * @param zradius
     * @param factory
     * @param deviceName
     * @param <T>
     * @return
     */
    public static <T extends IntegerType<T>> Img<T> maxFilter3DWithRectangularKernel(RandomAccessibleInterval<T> input,
                                                                                     int xradius, int yradius, int zradius,
                                                                                     ImgFactory<T> factory,
                                                                                     String deviceName) {
        long startTime = System.currentTimeMillis();
        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Shape inputShape = Shape.of(
                    input.dimension(2)/*dim-z*/,
                    input.dimension(1)/*dim-y*/,
                    input.dimension(0)/*dim-z*/,
                    1L /*channels*/
            );
            Shape kernelShape = Shape.of(
                    2L * zradius + 1,
                    2L * yradius + 1,
                    2L * xradius + 1
            );

            // Convert input to NDArray
            Operand<TFloat32> ndInput = tf.dtypes.cast(
                    tf.constant(inputShape, TensorflowUtils.createGrayIntDataFromGrayImg(input)),
                    TFloat32.class
            );

            Operand<TFloat32> reshapedNdInputWithBatches = tf.reshape(
                    ndInput,
                    tf.constant(new long[]{
                            1L,
                            inputShape.get(0),
                            inputShape.get(1),
                            inputShape.get(2),
                            inputShape.get(3)
                    })
            );
            Operand<TFloat32> maxFilter = tf.nn.maxPool3d(
                    reshapedNdInputWithBatches,
                    Arrays.asList(1L, kernelShape.get(0), kernelShape.get(1), kernelShape.get(2), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L, 1L),
                    "SAME",
                    MaxPool3d.dataFormat("NDHWC")
            );
            Operand<TInt32> ndOutput = tf.dtypes.cast(maxFilter, TInt32.class);
            // Execute the graph to get the result
            try (Session session = TensorflowUtils.createSession(execEnv);
                 Tensor result = session.runner().fetch(ndOutput).run().get(0)) {
                LOG.info("MaxFilter with radius {}:{}:{} took {} secs -> {}",
                        xradius, yradius, zradius,
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        result);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyPixelIntDataToGrayImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }

    }

    public static <T extends RGBPixelType<T>> Img<T> maxFilter2DRGBWithEllipticalKernel(RandomAccessibleInterval<T> input,
                                                                                        int xRadius, int yRadius,
                                                                                        long blockSizeX, long blockSizeY,
                                                                                        ImgFactory<T> factory,
                                                                                        String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(3L, input.dimension(1), input.dimension(0), 1L);
        // Convert input to NDArray
        IntNdArray ndInput = NdArrays.wrap(inputShape, TensorflowUtils.createRGBIntDataFromRGBImg(input));
        IntNdArray kernel = createKernel(xRadius, yRadius);

        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            // set the block partitions - this creates a stack of [blockStart, blockEnd] coordinates
            int batchedChannels = 3;
            List<Operand<TInt32>> blocksIndicesList = new ArrayList<>();
            for (int c = 0; c < inputShape.get(0); c += batchedChannels) {
                for (long h = 0, hi = 0; h < inputShape.get(1); h += blockSizeY, hi++) {
                    for (long w = 0, wi = 0; w < inputShape.get(2); w += blockSizeX, wi++) {
                        Index[] blockIndex = getBlockIndex(
                                inputShape,
                                new long[]{h, w},
                                new long[]{blockSizeY, blockSizeX},
                                new int[]{hi % 2 == 0 ? yRadius : 0, wi % 2 == 0 ? xRadius : 0}
                        );
                        blocksIndicesList.add(tf.constant(new int[]{
                                c, (int) blockIndex[0].begin(), (int) blockIndex[1].begin(), 0}));
                        blocksIndicesList.add(tf.constant(new int[]{
                                c + batchedChannels, (int) blockIndex[0].end(), (int) blockIndex[1].end(), 1}));
                    }
                }
            }

            ConcreteFunction loopBody = ConcreteFunction.create(
                    (forOps) -> {
                        Operand<TInt32> loopIndex = forOps.placeholder(TInt32.class); // this is passed implicitly by tensorflow
                        Operand<TInt32> inputImage = forOps.placeholder(TInt32.class);
                        Operand<TInt32> resultImage = forOps.placeholder(TInt32.class);
                        Operand<TInt32> blocksIndices = forOps.placeholder(TInt32.class);

                        Operand<TInt32> bstartRow = forOps.math.mul(loopIndex, forOps.constant(2));
                        Operand<TInt32> bendRow = forOps.math.add(bstartRow, forOps.constant(1));
                        Operand<TInt32> bstart = forOps.reshape(forOps.slice(
                                blocksIndices,
                                forOps.math.mul(bstartRow, forOps.constant(new int[]{1, 0})),
                                forOps.constant(new int[]{1, -1})
                        ), forOps.constant(new int[]{-1}));
                        Operand<TInt32> bend = forOps.reshape(forOps.slice(
                                blocksIndices,
                                forOps.math.mul(bendRow, forOps.constant(new int[]{1, 0})),
                                forOps.constant(new int[]{1, -1})
                        ), forOps.constant(new int[]{-1}));
                        Operand<TInt32> bsize = forOps.math.sub(bend, bstart);
                        Operand<TInt32> currentBlock = forOps.slice(inputImage, bstart, bsize);

                        ConcreteFunction thenBranch = ConcreteFunction.create(
                                thenOps -> {
                                    Operand<TInt32> blockInput = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockStart = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockEnd = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> resultInput = thenOps.placeholder(TInt32.class);

                                    Operand<TInt32> tkernel = thenOps.constant(kernel);
                                    Operand<TInt32> maxFilterBlock = maxFilter2DBlock(thenOps, blockInput, tkernel);

                                    // extend the block to the entire image by padding to the "left" and to the "right" of the block
                                    Operand<TInt32> rightPad = thenOps.math.sub(thenOps.shape(resultInput), blockEnd);

                                    Operand<TInt32> extendedMaxFilterBlock = thenOps.pad(
                                            maxFilterBlock,
                                            thenOps.stack(Arrays.asList(blockStart, rightPad), Stack.axis(1L)),
                                            thenOps.constant(0)
                                    );

                                    Operand<TInt32> updatedResult = thenOps.math.maximum(resultInput, extendedMaxFilterBlock);

                                    return Signature.builder()
                                            .input("block", blockInput)
                                            .input("blockStart", blockStart)
                                            .input("blockEnd", blockEnd)
                                            .input("result", resultInput)
                                            .output("result", updatedResult)
                                            .build();
                                }
                        );
                        ConcreteFunction elseBranch = ConcreteFunction.create(
                                elseOps -> {
                                    Operand<TInt32> blockInput = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockStart = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockEnd = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> resultInput = elseOps.placeholder(TInt32.class);
                                    // return the result unchanged
                                    return Signature.builder()
                                            .input("block", blockInput)
                                            .input("blockStart", blockStart)
                                            .input("blockEnd", blockEnd)
                                            .input("result", resultInput)
                                            .output("result", resultInput)
                                            .build();
                                }
                        );

                        If condResult = forOps.statelessIf(
                                forOps.math.greater(
                                        forOps.reduceMax(currentBlock, forOps.constant(new int[]{0, 1, 2, 3})),
                                        forOps.constant(0)
                                ),
                                Arrays.asList(currentBlock, bstart, bend, resultImage),
                                Collections.singletonList(TInt32.class),
                                thenBranch,
                                elseBranch,
                                If.outputShapes(resultImage.shape())
                        );

                        return Signature.builder()
                                .input("loopIndex", loopIndex)
                                .input("inputImage", inputImage)
                                .input("resultImage", resultImage)
                                .input("blocksIndices", blocksIndices)
                                .output("inputImage", inputImage)
                                .output("resultImage", condResult.output().get(0))
                                .output("blocksIndices", blocksIndices)
                                .build();
                    }
            );
            Operand<TInt32> tInput = tf.constant(ndInput);
            Operand<TInt32> blocksIndicesStack = tf.stack(blocksIndicesList);
            Operand<TInt32> maxFilter = tf.zeros(tf.constant(inputShape.asArray()), TInt32.class);


            // iterate over the blocks
            For blocksLoop = tf.forOp(
                    tf.constant(0),
                    tf.constant(blocksIndicesList.size() / 2),
                    tf.constant(1),
                    Arrays.asList(tInput, maxFilter, blocksIndicesStack),
                    loopBody
            );


            try (Session session = TensorflowUtils.createSession(execEnv);
                 Tensor result = session.runner().fetch(blocksLoop.output().get(1)).run().get(0)) {
                LOG.info("Max filter {}:{} for {} image took {} secs -> {}",
                        xRadius, yRadius, inputShape,
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        maxFilter);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyRGBIntDataToRGBImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

    public static <T extends IntegerType<T>> Img<T> maxFilter3DGrayWithEllipsoidKernel(RandomAccessibleInterval<T> input,
                                                                                       int xRadius, int yRadius, int zRadius,
                                                                                       long blockSizeX, long blockSizeY, long blockSizeZ,
                                                                                       ImgFactory<T> factory,
                                                                                       String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(1L, input.dimension(2), input.dimension(1), input.dimension(0), 1L);
        // Convert input to NDArray
        IntNdArray ndInput = NdArrays.wrap(inputShape, TensorflowUtils.createGrayIntDataFromGrayImg(input));
        IntNdArray kernel = createKernel(xRadius, yRadius, zRadius);

        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            // set the block partitions - this creates a stack of [blockStart, blockEnd] coordinates
            List<Operand<TInt32>> blocksIndicesList = new ArrayList<>();
            for (long d = 0, di = 0; d < inputShape.get(1); d += blockSizeZ, di++) {
                for (long h = 0, hi = 0; h < inputShape.get(2); h += blockSizeY, hi++) {
                    for (long w = 0, wi = 0; w < inputShape.get(3); w += blockSizeX, wi++) {
                        Index[] blockIndex = getBlockIndex(
                                inputShape,
                                new long[]{d, h, w},
                                new long[]{blockSizeZ, blockSizeY, blockSizeX},
                                new int[]{di % 2 == 0 ? zRadius : 0, hi % 2 == 0 ? yRadius : 0, wi % 2 == 0 ? xRadius : 0}
                        );
                        blocksIndicesList.add(tf.constant(new int[]{
                                0, (int) blockIndex[0].begin(), (int) blockIndex[1].begin(), (int) blockIndex[2].begin(), 0}));
                        blocksIndicesList.add(tf.constant(new int[]{
                                1, (int) blockIndex[0].end(), (int) blockIndex[1].end(), (int) blockIndex[2].end(), 1}));
                    }
                }
            }

            ConcreteFunction loopBody = ConcreteFunction.create(
                    (forOps) -> {
                        Operand<TInt32> loopIndex = forOps.placeholder(TInt32.class); // this is passed implicitly by tensorflow
                        Operand<TInt32> inputImage = forOps.placeholder(TInt32.class);
                        Operand<TInt32> resultImage = forOps.placeholder(TInt32.class);
                        Operand<TInt32> blocksIndices = forOps.placeholder(TInt32.class);

                        // get the block start, block end and calculate the block size
                        Operand<TInt32> bstartRow = forOps.math.mul(loopIndex, forOps.constant(2));
                        Operand<TInt32> bendRow = forOps.math.add(bstartRow, forOps.constant(1));
                        Operand<TInt32> bstart = forOps.reshape(forOps.slice(
                                blocksIndices,
                                forOps.math.mul(bstartRow, forOps.constant(new int[]{1, 0})),
                                forOps.constant(new int[]{1, -1})
                        ), forOps.constant(new int[]{-1}));
                        Operand<TInt32> bend = forOps.reshape(forOps.slice(
                                blocksIndices,
                                forOps.math.mul(bendRow, forOps.constant(new int[]{1, 0})),
                                forOps.constant(new int[]{1, -1})
                        ), forOps.constant(new int[]{-1}));
                        Operand<TInt32> bsize = forOps.math.sub(bend, bstart);
                        Operand<TInt32> currentBlock = forOps.slice(inputImage, bstart, bsize);

                        ConcreteFunction thenBranch = ConcreteFunction.create(
                                thenOps -> {
                                    Operand<TInt32> blockInput = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockStart = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockEnd = thenOps.placeholder(TInt32.class);
                                    Operand<TInt32> resultInput = thenOps.placeholder(TInt32.class);

                                    Operand<TInt32> tkernel = thenOps.constant(kernel);
                                    Operand<TInt32> maxFilterBlock = maxFilter3DBlock(thenOps, blockInput, tkernel);

                                    // extend the block to the entire image by padding to the "left" and to the "right" of the block
                                    Operand<TInt32> rightPad = thenOps.math.sub(thenOps.shape(resultInput), blockEnd);

                                    Operand<TInt32> extendedMaxFilterBlock = thenOps.pad(
                                            maxFilterBlock,
                                            thenOps.stack(Arrays.asList(blockStart, rightPad), Stack.axis(1L)),
                                            thenOps.constant(0)
                                    );

                                    Operand<TInt32> updatedResult = thenOps.math.maximum(resultInput, extendedMaxFilterBlock);

                                    return Signature.builder()
                                            .input("block", blockInput)
                                            .input("blockStart", blockStart)
                                            .input("blockEnd", blockEnd)
                                            .input("result", resultInput)
                                            .output("result", updatedResult)
                                            .build();

                                }
                        );

                        ConcreteFunction elseBranch = ConcreteFunction.create(
                                elseOps -> {
                                    Operand<TInt32> blockInput = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockStart = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> blockEnd = elseOps.placeholder(TInt32.class);
                                    Operand<TInt32> resultInput = elseOps.placeholder(TInt32.class);
                                    // return the result unchanged
                                    return Signature.builder()
                                            .input("block", blockInput)
                                            .input("blockStart", blockStart)
                                            .input("blockEnd", blockEnd)
                                            .input("result", resultInput)
                                            .output("result", resultInput)
                                            .build();
                                }
                        );

                        If condResult = forOps.statelessIf(
                                forOps.math.greater(
                                        forOps.reduceMax(currentBlock, forOps.constant(new int[]{0, 1, 2, 3, 4})),
                                        forOps.constant(0)
                                ),
                                Arrays.asList(currentBlock, bstart, bend, resultImage),
                                Collections.singletonList(TInt32.class),
                                thenBranch,
                                elseBranch,
                                If.outputShapes(resultImage.shape())
                        );

                        return Signature.builder()
                                .input("loopIndex", loopIndex)
                                .input("inputImage", inputImage)
                                .input("resultImage", resultImage)
                                .input("blocksIndices", blocksIndices)
                                .output("inputImage", inputImage)
                                .output("resultImage", condResult.output().get(0))
                                .output("blocksIndices", blocksIndices)
                                .build();
                    }
            );
            Operand<TInt32> tInput = tf.constant(ndInput);
            Operand<TInt32> blocksIndicesStack = tf.stack(blocksIndicesList);
            Operand<TInt32> maxFilter = tf.zeros(tf.constant(inputShape.asArray()), TInt32.class);

            // iterate over the blocks
            For blocksLoop = tf.forOp(
                    tf.constant(0),
                    tf.constant(blocksIndicesList.size() / 2),
                    tf.constant(1),
                    Arrays.asList(tInput, maxFilter, blocksIndicesStack),
                    loopBody
            );

            try (Session session = TensorflowUtils.createSession(execEnv);
                 Tensor result = session.runner().fetch(blocksLoop.output().get(1)).run().get(0)) {
                LOG.info("Max filter {}:{}:{} for {} image took {} secs -> {}",
                        xRadius, yRadius, zRadius, inputShape,
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        maxFilter);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyPixelIntDataToGrayImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

    private static IntNdArray createKernel(int... radii) {
        long[] shapeSizes = new long[radii.length];
        for (int i = 0; i < radii.length; i++) {
            shapeSizes[radii.length - i - 1] = 2L * radii[i] + 1L;
        }
        Shape kernelShape = Shape.of(shapeSizes);
        HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(radii);
        IntDataBuffer kernelDataBuffer = DataBuffers.of(kernelMask.getKernelMask());
        return NdArrays.wrap(kernelShape, kernelDataBuffer);
    }

    private static Index[] getBlockIndex(Shape inputShape,
                                         long[] blockStartCoords,
                                         long[] blockDims,
                                         int[] overlaps) {
        LOG.debug("Get block index at {}", Arrays.toString(blockStartCoords));
        Index[] blockIndex = new Index[blockStartCoords.length];
        for (int i = 0; i < blockStartCoords.length; i++) {
            blockIndex[i] = Indices.range(
                    Math.max(blockStartCoords[i] - overlaps[i], 0),
                    Math.min(blockStartCoords[i] + blockDims[i] + overlaps[i], inputShape.get(i + 1))
            );
        }
        return blockIndex;
    }

    /**
     * Compute max filter for a 2D block using the given kernel. The kernel typically is non-rectangular,
     * rectangular kernels are directly supported by maxPool2D.
     *
     * @param tf Tensorflow ops
     * @param inputBlock
     * @param kernel
     * @return
     */
    private static Operand<TInt32> maxFilter2DBlock(Ops tf, Operand<TInt32> inputBlock, Operand<TInt32> kernel) {
        Operand<TInt32> blockPatches = tf.image.extractImagePatches(
                inputBlock,
                Arrays.asList(1L, kernel.shape().get(0), kernel.shape().get(1), 1L),
                Arrays.asList(1L, 1L, 1L, 1L),
                Arrays.asList(1L, 1L, 1L, 1L),
                "SAME"
        );
        Operand<TInt32> reshapedBlockPatches = tf.reshape(
                blockPatches,
                tf.concat(
                        Arrays.asList(
                                tf.slice(tf.shape(inputBlock), tf.constant(new int[]{0}), tf.constant(new int[]{3})),
                                tf.shape(kernel)
                        ),
                        tf.constant(0))
        );
        Operand<TInt32> maskedBlockPatches = tf.math.mul(reshapedBlockPatches, kernel);
        return tf.reduceMax(
                tf.reshape(
                        maskedBlockPatches,
                        tf.concat(
                                Arrays.asList(
                                        tf.shape(inputBlock),
                                        tf.expandDims(tf.size(kernel), tf.constant(0))
                                ),
                                tf.constant(0)
                        )
                ),
                tf.constant(-1)
        );
    }

    /**
     * Compute max filter for a 3D block using the given kernel. The kernel typically is non-rectangular,
     * rectangular kernels are directly supported by maxPool3D.
     *
     * @param tf Tensorflow ops
     * @param inputBlock
     * @param kernel
     * @return
     */
    private static Operand<TInt32> maxFilter3DBlock(Ops tf, Operand<TInt32> inputBlock, Operand<TInt32> kernel) {
        Operand<TInt32> blockPatches = tf.extractVolumePatches(
                inputBlock,
                Arrays.asList(1L, kernel.shape().get(0), kernel.shape().get(1), kernel.shape().get(2), 1L),
                Arrays.asList(1L, 1L, 1L, 1L, 1L),
                "SAME"
        );
        Operand<TInt32> reshapedBlockPatches = tf.reshape(
                blockPatches,
                tf.concat(
                        Arrays.asList(
                            tf.slice(tf.shape(inputBlock), tf.constant(new int[]{0}), tf.constant(new int[]{4})),
                            tf.shape(kernel)
                        ),
                        tf.constant(0))
        );
        Operand<TInt32> maskedBlockPatches = tf.math.mul(reshapedBlockPatches, kernel);
        return tf.reduceMax(
                tf.reshape(
                        maskedBlockPatches,
                        tf.concat(
                            Arrays.asList(
                                    tf.shape(inputBlock),
                                    tf.expandDims(tf.size(kernel), tf.constant(0))
                            ),
                            tf.constant(0)
                        )
                ),
                tf.constant(-1)
        );
    }

}
