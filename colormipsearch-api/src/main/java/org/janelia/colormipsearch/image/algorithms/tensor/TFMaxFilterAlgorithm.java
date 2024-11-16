package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.Arrays;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.DeviceSpec;
import org.tensorflow.EagerSession;
import org.tensorflow.Operand;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.IntNdArray;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.ndarray.index.Index;
import org.tensorflow.ndarray.index.Indices;
import org.tensorflow.op.Ops;
import org.tensorflow.op.nn.MaxPool3d;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

public class TFMaxFilterAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(TFMaxFilterAlgorithm.class);

    /**
     * Perform a max filter operation on a 2D image using a rectangular kernel. If any of the radii is 0, the kernel will
     * not be applied along that axis - so for example to perform a max filter only along the x-axis only, set yradius and zradius to 0.
     * @param input
     * @param xradius
     * @param yradius
     * @param zradius
     * @param factory
     * @param deviceName
     * @return
     * @param <T>
     */
    public static <T extends IntegerType<T>> Img<T> maxFilter3DWithRectangularKernel(RandomAccessibleInterval<T> input,
                                                                                     int xradius, int yradius, int zradius,
                                                                                     ImgFactory<T> factory,
                                                                                     String deviceName) {
        long startTime = System.currentTimeMillis();
        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
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
            try (Tensor result = ndOutput.asTensor()) {
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

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            Operand<TInt32> maxFilter = tf.zeros(tf.constant(inputShape.asArray()), TInt32.class);
            // iterate over the blocks
            int batchedChannels = 3;
            for (int c = 0; c < inputShape.get(0); c += batchedChannels) {
                for (long h = 0, hi = 0; h < inputShape.get(1); h += blockSizeY, hi++) {
                    for (long w = 0, wi = 0; w < inputShape.get(2); w += blockSizeX, wi++) {
                        Index[] blockIndex = getBlockIndex(
                                inputShape,
                                new long[]{h, w},
                                new long[]{blockSizeY, blockSizeX},
                                new int[]{hi % 2 == 0 ? yRadius : 0, wi % 2 == 0 ? xRadius : 0}
                        );
                        IntNdArray block = ndInput.slice(Indices.range(c, c+batchedChannels), blockIndex[0], blockIndex[1], Indices.range(0, inputShape.get(3)));

                        IntNdArray tMaxFilterBlock = maxFilter2DBlock(block, kernel, deviceName);
                        if (tMaxFilterBlock == null) {
                            continue;
                        }

                        Operand<TInt32> expandedMaxFilterBlock = tf.pad(
                                tf.constant(tMaxFilterBlock),
                                tf.constant(new long[][]{
                                        {c, inputShape.get(0) - (c + batchedChannels)},
                                        {blockIndex[0].begin(), inputShape.get(1) - blockIndex[0].end()},
                                        {blockIndex[1].begin(), inputShape.get(2) - blockIndex[1].end()},
                                        {0, 0}
                                }),
                                tf.constant(0)
                        );
                        maxFilter = tf.math.maximum(maxFilter, expandedMaxFilterBlock);
                    }
                }
            }
            try (Tensor result = maxFilter.asTensor()) {
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

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            Operand<TInt32> maxFilter = tf.zeros(tf.constant(inputShape.asArray()), TInt32.class);
            // iterate over the blocks
            for (long d = 0, di = 0; d < inputShape.get(1); d += blockSizeZ, di++) {
                for (long h = 0, hi = 0; h < inputShape.get(2); h += blockSizeY, hi++) {
                    for (long w = 0, wi = 0; w < inputShape.get(3); w += blockSizeX, wi++) {
                        Index[] blockIndex = getBlockIndex(
                                inputShape,
                                new long[]{d, h, w},
                                new long[]{blockSizeZ, blockSizeY, blockSizeX},
                                new int[]{di % 2 == 0 ? zRadius : 0, hi % 2 == 0 ? yRadius : 0, wi % 2 == 0 ? xRadius : 0}
                        );
                        IntNdArray block = ndInput.slice(Indices.range(0, 1), blockIndex[0], blockIndex[1], blockIndex[2], Indices.range(0, 1));

                        IntNdArray tMaxFilterBlock = maxFilter3DBlock(block, kernel, deviceName);
                        if (tMaxFilterBlock == null) {
                            continue;
                        }

                        Operand<TInt32> expandedMaxFilterBlock = tf.pad(
                                tf.constant(tMaxFilterBlock),
                                tf.constant(new long[][]{
                                        {0, 0},
                                        {blockIndex[0].begin(), inputShape.get(1) - blockIndex[0].end()},
                                        {blockIndex[1].begin(), inputShape.get(2) - blockIndex[1].end()},
                                        {blockIndex[2].begin(), inputShape.get(3) - blockIndex[2].end()},
                                        {0, 0}
                                }),
                                tf.constant(0)
                        );
                        maxFilter = tf.math.maximum(maxFilter, expandedMaxFilterBlock);
                    }
                }
            }
            try (Tensor result = maxFilter.asTensor()) {
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
     * @param tInputBlock
     * @param tKernel
     * @param deviceName
     * @return
     */
    private static IntNdArray maxFilter2DBlock(IntNdArray tInputBlock, IntNdArray tKernel, String deviceName) {

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Operand<TInt32> inputBlock = tf.constant(tInputBlock);

            Operand<TInt32> maxVal = tf.reduceMax(inputBlock, tf.constant(new long[]{0, 1, 2}));
            try (Tensor result = maxVal.asTensor()) {
                if (result.asRawTensor().data().asInts().getInt(0) == 0) {
                    return null;
                }
            }

            Operand<TInt32> kernel = tf.constant(tKernel);
            Operand<TInt32> blockPatches = tf.image.extractImagePatches(
                    inputBlock,
                    Arrays.asList(1L, kernel.shape().get(0), kernel.shape().get(1), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L),
                    Arrays.asList(1L, 1L, 1L, 1L),
                    "SAME"
            );
            Operand<TInt32> reshapedBlockPatches = tf.reshape(
                    blockPatches,
                    tf.constant(new long[]{
                            inputBlock.shape().get(0), // batch size
                            inputBlock.shape().get(1), inputBlock.shape().get(2),
                            kernel.shape().get(0), kernel.shape().get(1)
                    })
            );
            Operand<TInt32> maskedBlockPatches = tf.math.mul(reshapedBlockPatches, kernel);
            Operand<TInt32> maxFilterBlock = tf.reduceMax(
                    tf.reshape(
                            maskedBlockPatches,
                            tf.constant(new long[]{
                                    inputBlock.shape().get(0), // batch size
                                    inputBlock.shape().get(1), inputBlock.shape().get(2),
                                    inputBlock.shape().get(3), // channels
                                    kernel.shape().get(0) * kernel.shape().get(1)})
                    ),
                    tf.constant(-1)
            );
            try (Tensor result = maxFilterBlock.asTensor()) {
                return NdArrays.wrap(result.shape(), result.asRawTensor().data().asInts());
            }
        }
    }

    /**
     * Compute max filter for a 3D block using the given kernel. The kernel typically is non-rectangular,
     * rectangular kernels are directly supported by maxPool3D.
     *
     * @param tInputBlock
     * @param tKernel
     * @param deviceName
     * @return
     */
    private static IntNdArray maxFilter3DBlock(IntNdArray tInputBlock, IntNdArray tKernel, String deviceName) {

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Operand<TInt32> inputBlock = tf.constant(tInputBlock);

            Operand<TInt32> maxVal = tf.reduceMax(inputBlock, tf.constant(new long[]{0, 1, 2, 3}));
            try (Tensor result = maxVal.asTensor()) {
                if (result.asRawTensor().data().asInts().getInt(0) == 0) {
                    return null;
                }
            }

            Operand<TInt32> kernel = tf.constant(tKernel);
            Operand<TInt32> blockPatches = tf.extractVolumePatches(
                    inputBlock,
                    Arrays.asList(1L, kernel.shape().get(0), kernel.shape().get(1), kernel.shape().get(2), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L, 1L),
                    "SAME"
            );
            Operand<TInt32> reshapedBlockPatches = tf.reshape(
                    blockPatches,
                    tf.constant(new long[]{
                            inputBlock.shape().get(0), // batch size
                            inputBlock.shape().get(1), inputBlock.shape().get(2), inputBlock.shape().get(3),
                            kernel.shape().get(0), kernel.shape().get(1), kernel.shape().get(2)
                    })
            );
            Operand<TInt32> maskedBlockPatches = tf.math.mul(reshapedBlockPatches, kernel);
            Operand<TInt32> maxFilterBlock = tf.reduceMax(
                    tf.reshape(
                            maskedBlockPatches,
                            tf.constant(new long[]{
                                    inputBlock.shape().get(0), // batch size
                                    inputBlock.shape().get(1), inputBlock.shape().get(2), inputBlock.shape().get(3),
                                    inputBlock.shape().get(4), // channels
                                    kernel.shape().get(0) * kernel.shape().get(1) * kernel.shape().get(2)})
                    ),
                    tf.constant(-1)
            );
            try (Tensor result = maxFilterBlock.asTensor()) {
                return NdArrays.wrap(result.shape(), result.asRawTensor().data().asInts());
            }
        }
    }

}
