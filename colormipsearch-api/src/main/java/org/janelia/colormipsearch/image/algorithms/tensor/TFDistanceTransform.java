package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
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
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.ndarray.buffer.LongDataBuffer;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Where;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TInt64;

public class TFDistanceTransform {
    private static final Logger LOG = LoggerFactory.getLogger(TFDistanceTransform.class);

    public static <T extends RGBPixelType<T>> Img<UnsignedShortType> distanceTransform2DRGB(RandomAccessibleInterval<T> input, String deviceName) {
        long startTime = System.currentTimeMillis();
        long width = input.dimension(0);
        long height = input.dimension(1);
        Shape inputShape = Shape.of(height, width);
        // Convert input to NDArray
        IntNdArray ndInput = NdArrays.wrap(inputShape, TensorflowUtils.createGrayIntDataFromRGBImg(input));
        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            Operand<TFloat32> tNdInput = tf.dtypes.cast(
                    tf.constant(ndInput),
                    TFloat32.class
            );
            Operand<TFloat32> f = tf.select(tf.math.greater(tNdInput, tf.constant(0.f)),
                    tf.constant(0.f),
                    tf.constant(Float.MAX_VALUE));
            Operand<TFloat32> p = tf.reshape(
                    tf.range(tf.constant(0.f), tf.constant((float) height), tf.constant(1.f)),
                    tf.constant(new long[]{height, 1})
            );
            Operand<TFloat32> psq = tf.math.mul(p, p);
            Operand<TFloat32> f2 = tf.math.add(f, psq);

            Operand<TFloat32> zTensor = createZTensor(tf, (int) height);
            Operand<TInt64> zIndices = tf.math.argMin(zTensor, tf.constant(0));
            for (int qi = 1; qi < height; qi++) {
                Operand<TFloat32> s = intersection(tf, f, qi, qi-1);
            }

            //            for (int qi = 0; qi < height; qi++) {
//                Operand<TFloat32> qSlice = tf.slice(f2, tf.constant(new long[]{qi, 0}), tf.constant(new long[]{1, width}));
//                for (int pi = qi+1; pi < height; pi++) {
//                    Operand<TFloat32> pSlice = tf.slice(f2, tf.constant(new long[]{pi, 0}), tf.constant(new long[]{1, width}));
//                    Operand<TFloat32> s = tf.math.div(
//                            tf.math.sub(pSlice, qSlice),
//                            tf.constant(2.f * (pi - qi)));
//                }
//            }

//            for (long x = 0; x < width; x++) {
//                // Extract column x
//                Operand<TFloat32> fTensor = tf.slice(tNdInput, tf.constant(new long[]{0, x}), tf.constant(new long[]{height, 1}));
//                int k = 0;
//                // D(p) = min(d(p,q) + f(q))
//                for (int q = 1; q < height - 1; q++) {
////                    Operand<TFloat32> s = df(tf, fTensor, q, k);
//
////                    while (tf.math.lessEqual(s, tf.slice(zTensor, tf.constant(new int[]{k}), tf.constant(new int[]{1}))).asTensor().getBoolean(0, 0)) {
////                        k--;
////                        s = df(tf, fTensor, q, k);
////                    }
//                }
//            }

            Operand<TInt32> ndOutput = tf.dtypes.cast(f2, TInt32.class);
            try (Tensor result = ndOutput.asTensor()) {
                LOG.info("Completed distance transform of " + inputShape + " image in " + (System.currentTimeMillis() - startTime) / 1000. + " secs -> " + result);
                Img<UnsignedShortType> output = new ArrayImgFactory<>(new UnsignedShortType()).create(
                        result.shape().get(1), result.shape().get(0)
                );
                TensorflowUtils.copyPixelIntDataToGrayImg(result.asRawTensor().data().asInts(), output);
                return output;

            }
            // TODO: implement distance transform
        }
    }

    private static Operand<TFloat32> createZTensor(Ops tf, int size) {
        float[] zValues = new float[size];
        zValues[0] = Float.MIN_VALUE;
        zValues[1] = Float.MAX_VALUE;
        for (int i = 2; i < size; i++) {
            zValues[i] = 0;
        }
        return tf.constant(zValues);
    }

    private static Operand<TFloat32> intersection(Ops tf, Operand<TFloat32> f, int q, int k) {
        Operand<TFloat32> fq = tf.slice(f, tf.constant(new int[]{q, 0}), tf.constant(new int[]{1, 1}));
        Operand<TFloat32> fv = tf.slice(f, tf.constant(new int[]{k, 0}), tf.constant(new int[]{1, 1}));
        return tf.math.div(
                tf.math.sub(
                        tf.math.add(fq, tf.constant((float) q * q)),
                        tf.math.add(fv, tf.constant((float) k * k))),
                tf.constant(2.0f * q - 2.0f * k)
        );
    }
}
