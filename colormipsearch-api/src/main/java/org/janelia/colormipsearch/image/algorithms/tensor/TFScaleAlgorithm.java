package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.DeviceSpec;
import org.tensorflow.EagerSession;
import org.tensorflow.Operand;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Constant;
import org.tensorflow.op.core.ReduceSum;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TInt64;
import org.tensorflow.types.family.TNumber;

public class TFScaleAlgorithm {
    private static final Logger LOG = LoggerFactory.getLogger(TFScaleAlgorithm.class);
    private static final double ALPHA = 0.5; // Catmull-Rom interpolation

    public static <T extends IntegerType<T> & NativeType<T>> Img<T> scale3DImage(RandomAccessibleInterval<T> input,
                                                                                 long dstWidth, long dstHeight, long dstDepth,
                                                                                 T pxType,
                                                                                 String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(input.dimension(2), input.dimension(1), input.dimension(0));
        Shape outputShape = Shape.of(dstDepth, dstHeight, dstWidth);
        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            // Convert input to NDArray
            IntDataBuffer inputDataBuffer = TensorflowUtils.createGrayIntDataFromGrayImg(input);
            Operand<TFloat32> ndInput = tf.dtypes.cast(
                    tf.constant(inputShape, inputDataBuffer),
                    TFloat32.class
            );
            Operand<TFloat32> interpolatedZ = interpolate3D(tf, ndInput, inputShape, outputShape, 0, TFloat32.class);
            Operand<TFloat32> interpolatedY = interpolate3D(tf, interpolatedZ, inputShape, outputShape, 1, TFloat32.class);
            Operand<TFloat32> interpolatedX = interpolate3D(tf, interpolatedY, inputShape, outputShape, 2, TFloat32.class);

            Operand<TInt32> ndOutput = tf.dtypes.cast(interpolatedX, TInt32.class);

            try (Tensor result = ndOutput.asTensor()) {
                LOG.info("Completed scale of {} image to {} image in {} secs -> {}",
                        inputShape,
                        outputShape,
                        (System.currentTimeMillis() - startTime) / 1000.,
                        result);
                Img<T> output = new ArrayImgFactory<>(pxType).create(
                        result.shape().get(2), result.shape().get(1), result.shape().get(0)
                );
                TensorflowUtils.copyPixelIntDataToGrayImg(result.asRawTensor().data().asInts(), output);
                return output;
            }
        }
    }

    private static <T extends TNumber> Operand<T> interpolate3D(Ops tf, Operand<T> input, Shape sourceShape, Shape targetShape, int axis, Class<T> type) {
        long newSize =  targetShape.get(axis);
        double scaleToSourceFactor = (double) sourceShape.get(axis) / newSize;
        List<Operand<T>> slices = new ArrayList<>();
        for (long i = 0; i < newSize; i++) {
            double t0d = i * scaleToSourceFactor;
            long t0l = (long) Math.floor(t0d);
            long start;
            int windowSize;
            if (t0l > 0) {
                // start with P0
                start = t0l - 1;
                windowSize = 4;
            } else {
                // start with P1 and adjust the window size
                start = t0l;
                windowSize = 3;
            }
            double xstart = start - t0d;
            if (t0l + 2 >= sourceShape.get(axis)) {
                // adjust the window size to not go past the end
                windowSize -= (int)(t0l + 2 - (sourceShape.get(axis) - 1));
            }
            // if the window size is negative is handled by throwing an exception
            if (windowSize < 0) {
                throw new IllegalArgumentException("Case is not supported");
            }
            Operand<TInt64> pStart = tf.constant(new long[]{
                    axis == 0 ? start : 0,
                    axis == 1 ? start : 0,
                    axis == 2 ? start : 0,
            });
            Constant<TInt64> sliceSz = tf.constant(new long[] {
                    axis == 0 ? windowSize : -1,
                    axis == 1 ? windowSize : -1,
                    axis == 2 ? windowSize : -1,
            });
            Operand<T> p = tf.slice(input, pStart, sliceSz);
            float[] cubicValues = new float[windowSize];
            for (int j = 0; j < windowSize; j++) {
                cubicValues[j] = cubic(Math.abs(xstart + j));
            }
            int[] kernelShape = new int[] {
                    axis == 0 ? cubicValues.length : 1,
                    axis == 1 ? cubicValues.length : 1,
                    axis == 2 ? cubicValues.length : 1,
            };
            Operand<T> kernel = tf.dtypes.cast(tf.reshape(
                    tf.constant(cubicValues),
                    tf.constant(kernelShape)
            ), type);
            Operand<T> pTerms = tf.math.mul(
                    p, kernel
            );
            Operand<T> interpolatedValue = tf.reduceSum(pTerms, tf.constant(axis), ReduceSum.keepDims(true));
            slices.add(interpolatedValue);
        }
        return tf.concat(slices, tf.constant(axis));
    }

    private static float cubic(double x) {
        if (x <= 1.0)
            return (float) (3.0 * ALPHA * x * x * x - 5.0 * ALPHA * x * x + 2.0 * ALPHA);
        else if (x <= 2.0)
            return (float) (-ALPHA * x * x * x + 5.0 * ALPHA * x * x - 8.0 * ALPHA * x + 4.0 * ALPHA);
        else
            return 0.0f;
    }

}
