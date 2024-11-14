package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.DeviceSpec;
import org.tensorflow.EagerSession;
import org.tensorflow.Operand;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.IntNdArray;
import org.tensorflow.ndarray.NdArray;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.ndarray.index.Index;
import org.tensorflow.ndarray.index.Indices;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Constant;
import org.tensorflow.op.nn.MaxPool3d;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.family.TNumber;

public class TFMaxFilterAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(TFMaxFilterAlgorithm.class);

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
                    tf.constant(inputShape, TensorflowUtils.createIntDataFromSingleChannelImg(input)),
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
                LOG.info("MaxFilter with radius {}:{}:{} along axis {} took {} secs -> {}",
                        xradius, yradius, zradius,
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        result);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyIntDataToSingleChannelImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }

    }

    public static <T extends RGBPixelType<T>> Img<T> maxFilter2DWithEllipticalKernel(RandomAccessibleInterval<T> input,
                                                                                     int xRadius, int yRadius,
                                                                                     ImgFactory<T> factory,
                                                                                     String deviceName) {
        long startTime = System.currentTimeMillis();
        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            long[] shapeValues = {
                    input.dimension(1), input.dimension(0)
            };
            Shape inputShape = Shape.of(3L, shapeValues[0]/*dim-y*/, shapeValues[1]/*dim-x*/, 1L);
            Shape kernelShape = Shape.of(2L * yRadius + 1, 2L * xRadius + 1);

            // Convert input to NDArray
            IntDataBuffer inputDataBuffer = TensorflowUtils.createIntDataFromRGBImg(input);
            Constant<TInt32> ndInput = tf.constant(inputShape, inputDataBuffer);

            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius);
            IntDataBuffer kernelDataBuffer = DataBuffers.of(kernelMask.getKernelMask());
            Constant<TInt32> ndKernel = tf.constant(kernelShape, kernelDataBuffer);
            Operand<TInt32> ndInputImagePatches = tf.image.extractImagePatches(
                    ndInput,
                    Arrays.asList(1L, kernelShape.get(0), kernelShape.get(1), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L),
                    Arrays.asList(1L, 1L, 1L, 1L),
                    "SAME"
            );
            Operand<TInt32> reshapedImagePatchesWithKernelSize = tf.reshape(
                    ndInputImagePatches,
                    tf.constant(new long[]{3L, shapeValues[0], shapeValues[1], kernelShape.get(0), kernelShape.get(1)})
            );
            Operand<TInt32> maskedImagePatches = tf.math.mul(
                    reshapedImagePatchesWithKernelSize,
                    tf.reshape(ndKernel, tf.constant(new long[]{kernelShape.get(0), kernelShape.get(1)}))
            );
            Operand<TInt32> maxFilter = tf.reduceMax(
                    tf.reshape(
                            maskedImagePatches,
                            tf.constant(new long[]{3L, shapeValues[0], shapeValues[1], kernelShape.get(0) * kernelShape.get(1)})
                    ),
                    tf.constant(-1)
            );

            try (Tensor result = maxFilter.asTensor()) {
                LOG.info("Completed dilation for {}:{} in {} secs -> {}",
                        xRadius, yRadius,
                        (System.currentTimeMillis() - startTime) / 1000.,
                        result);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyIntDataToRGBImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

    public static <T extends IntegerType<T>> Img<T> maxFilter3DWithEllipsoidKernel(RandomAccessibleInterval<T> input,
                                                                                   int xRadius, int yRadius, int zRadius,
                                                                                   ImgFactory<T> factory,
                                                                                   String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(1L, input.dimension(2), input.dimension(1), input.dimension(0), 1L);
        // Convert input to NDArray
        IntNdArray ndInput = NdArrays.wrap(inputShape, TensorflowUtils.createIntDataFromSingleChannelImg(input));
        IntNdArray kernel = createKernel(xRadius, yRadius, zRadius);
        long blockSizeZ = zRadius * 32;
        long blockSizeY = yRadius * 32;
        long blockSizeX = xRadius * 32;

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            Operand<TInt32> maxFilter = tf.zeros(tf.constant(inputShape.asArray()), TInt32.class);
            // iterate over the blocks
            for (long d = 0, di = 0; d < inputShape.get(1); d += blockSizeZ, di++) {
                for (long h = 0, hi = 0; h < inputShape.get(2); h += blockSizeY, hi++) {
                    for (long w = 0, wi = 0; w < inputShape.get(3); w += blockSizeX, wi++) {
                        Pair<Index[], NdArray<Integer>> blockWithCoords = getBlock(
                                ndInput,
                                d, h, w,
                                blockSizeZ, blockSizeY, blockSizeX,
                                di % 2 == 0 ? zRadius : 0, hi % 2 == 0 ? yRadius : 0, wi % 2 == 0 ? xRadius : 0
                        );
                        IntNdArray block = (IntNdArray) blockWithCoords.getRight();

                        if (isEmpty(block, deviceName)) {
                            continue;
                        }

                        IntNdArray tMaxFilterBlock = maxFilter3DBlockWithEllipsoidKernel(block, kernel, deviceName, TInt32.class);
                        Index[] blockCoords = blockWithCoords.getLeft();

                        Operand<TInt32> expandedMaxFilterBlock = tf.pad(
                                tf.constant(tMaxFilterBlock),
                                tf.constant(new long[][]{
                                        {0, 0},
                                        {blockCoords[0].begin(), inputShape.get(1) - blockCoords[0].end()},
                                        {blockCoords[1].begin(), inputShape.get(2) - blockCoords[1].end()},
                                        {blockCoords[2].begin(), inputShape.get(3) - blockCoords[2].end()},
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
                TensorflowUtils.copyIntDataToSingleChannelImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

    private static IntNdArray createKernel(int xRadius, int yRadius, int zRadius) {
        Shape kernelShape = Shape.of(2L * zRadius + 1, 2L * yRadius + 1, 2L * xRadius + 1);
        HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
        IntDataBuffer kernelDataBuffer = DataBuffers.of(kernelMask.getKernelMask());
        return NdArrays.wrap(kernelShape, kernelDataBuffer);
    }

    private static <T> Pair<Index[], NdArray<T>> getBlock(NdArray<T> ndInput,
                                                          long d, long h, long w,
                                                          long blockSizeZ, long blockSizeY, long blockSizeX,
                                                          int overlapZ, int overlapY, int overlapX) {
        LOG.info("Get block at: {}:{}:{}", d, h, w);
        Index[] blockSlice = new Index[]{
                Indices.range(Math.max(d - overlapZ, 0), Math.min(d + blockSizeZ + overlapZ, ndInput.shape().get(1))),
                Indices.range(Math.max(h - overlapY, 0), Math.min(h + blockSizeY + overlapY, ndInput.shape().get(2))),
                Indices.range(Math.max(w - overlapX, 0), Math.min(w + blockSizeX + overlapX, ndInput.shape().get(3))),
        };
        return Pair.of(
                blockSlice,
                ndInput.slice(Indices.range(0, 1), blockSlice[0], blockSlice[1], blockSlice[2], Indices.range(0, 1))
        );
    }

    private static boolean isEmpty(IntNdArray tInputBlock, String deviceName) {

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Operand<TInt32> inputBlock = tf.constant(tInputBlock);
            Operand<TInt32> maxVal = tf.reduceMax(inputBlock, tf.constant(new long[]{0,1,2,3}));
            try (Tensor result = maxVal.asTensor()) {
                return result.asRawTensor().data().asInts().getInt(0) == 0;
            }
        }
    }

    private static <T extends TNumber> IntNdArray maxFilter3DBlockWithEllipsoidKernel(IntNdArray tInputBlock,
                                                                                      IntNdArray tKernel,
                                                                                      String deviceName,
                                                                                      Class<T> type) {

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Operand<T> inputBlock = tf.dtypes.cast(tf.constant(tInputBlock), type);
            Operand<T> kernel = tf.dtypes.cast(tf.constant(tKernel), type);
            Operand<T> blockPatches = tf.extractVolumePatches(
                    inputBlock,
                    Arrays.asList(1L, kernel.shape().get(0), kernel.shape().get(1), kernel.shape().get(2), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L, 1L),
                    "SAME"
            );
            Operand<T> reshapedBlockPatches = tf.reshape(
                    blockPatches,
                    tf.constant(new long[]{
                            inputBlock.shape().get(0), // batch size
                            inputBlock.shape().get(1), inputBlock.shape().get(2), inputBlock.shape().get(3),
                            kernel.shape().get(0), kernel.shape().get(1), kernel.shape().get(2)
                    })
            );
            Operand<T> maskedBlockPatches = tf.math.mul(reshapedBlockPatches, kernel);
            Operand<T> maxFilterBlock = tf.reduceMax(
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
