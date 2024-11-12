package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.Arrays;

import net.imglib2.Cursor;
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
import org.tensorflow.TensorFlow;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Constant;
import org.tensorflow.types.TInt32;

public class TFMaxFilterAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(TFMaxFilterAlgorithm.class);

    public static <T extends RGBPixelType<T>> Img<T> dilate2D(RandomAccessibleInterval<T> input,
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

    public static <T extends IntegerType<T>> Img<T> dilate3D(RandomAccessibleInterval<T> input,
                                                             int xRadius, int yRadius, int zRadius,
                                                             ImgFactory<T> factory,
                                                             String deviceName) {
        long startTime = System.currentTimeMillis();
        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            long[] shapeValues = {
                    input.dimension(2), input.dimension(1), input.dimension(0)
            };
            Shape inputShape = Shape.of(1L, shapeValues[0]/*dim-z*/, shapeValues[1]/*dim-y*/, shapeValues[2]/*dim-z*/, 1L);
            Shape kernelShape = Shape.of(2L * zRadius + 1, 2L * yRadius + 1, 2L * xRadius + 1);

            // Convert input to NDArray
            Constant<TInt32> ndInput = tf.constant(inputShape, TensorflowUtils.createIntDataFromSingleChannelImg(input));

            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
            IntDataBuffer kernelDataBuffer = DataBuffers.of(kernelMask.getKernelMask());
            Constant<TInt32> ndKernel = tf.constant(kernelShape, kernelDataBuffer);
            Operand<TInt32> ndInputImagePatches = tf.extractVolumePatches(
                    ndInput,
                    Arrays.asList(1L, kernelShape.get(0), kernelShape.get(1), kernelShape.get(2), 1L),
                    Arrays.asList(1L, 1L, 1L, 1L, 1L),
                    "SAME"
            );
            Operand<TInt32> reshapedImagePatchesWithKernelSize = tf.reshape(
                    ndInputImagePatches,
                    tf.constant(new long[]{1L, shapeValues[0], shapeValues[1], shapeValues[2], kernelShape.get(0), kernelShape.get(1), kernelShape.get(2)})
            );
            Operand<TInt32> maskedImagePatches = tf.math.mul(
                    reshapedImagePatchesWithKernelSize,
                    tf.reshape(ndKernel, tf.constant(new long[]{kernelShape.get(0), kernelShape.get(1), kernelShape.get(2)}))
            );
            Operand<TInt32> maxFilter = tf.reduceMax(
                    tf.reshape(
                            maskedImagePatches,
                            tf.constant(new long[]{1L, shapeValues[0], shapeValues[1], shapeValues[2], kernelShape.get(0) * kernelShape.get(1) * kernelShape.get(2)})
                    ),
                    tf.constant(-1)
            );

            // Execute the graph to get the result
            try (Tensor result = maxFilter.asTensor()) {
                LOG.info("Dilation {}:{}:{} took {} secs -> {}",
                        xRadius, yRadius, zRadius,
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        result);
                // Convert output tensor back to Img
                Img<T> output = factory.create(input);
                TensorflowUtils.copyIntDataToSingleChannelImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

}
