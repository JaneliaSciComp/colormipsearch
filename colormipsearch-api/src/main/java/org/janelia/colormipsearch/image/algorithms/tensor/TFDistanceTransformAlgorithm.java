package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Min;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TInt64;

public class TFDistanceTransformAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(TFDistanceTransformAlgorithm.class);

    public static <T extends RGBPixelType<T>> Img<UnsignedShortType> distanceTransform2DRGB(RandomAccessibleInterval<T> input, String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(input.dimension(1), input.dimension(0));
        IntNdArray ndInput = NdArrays.wrap(inputShape, TensorflowUtils.createGrayIntDataFromRGBImg(input));
        try (EagerSession execEnv = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            Operand<TFloat32> tNdInput = tf.dtypes.cast(
                    tf.constant(ndInput),
                    TFloat32.class
            );
            Operand<TFloat32> f = tf.select(tf.math.greater(tNdInput, tf.constant(0.f)),
                    tf.constant(0.f),
                    tf.constant(Float.MAX_VALUE));

            Operand<TFloat32> dty = dt(tf, f, f.shape().get(0), 0);
            Operand<TFloat32> dtx = dt(tf, dty, f.shape().get(1), 1);
            Operand<TInt32> ndOutput = tf.dtypes.cast(dtx, TInt32.class);
            try (Tensor result = ndOutput.asTensor()) {
                LOG.info("Completed distance transform of {} image in {} secs -> {}",
                        inputShape,
                        (System.currentTimeMillis() - startTime) / 1000.,
                        result);
                Img<UnsignedShortType> output = new ArrayImgFactory<>(new UnsignedShortType()).create(
                        result.shape().get(1), result.shape().get(0)
                );
                TensorflowUtils.copyPixelIntDataToGrayImg(result.asRawTensor().data().asInts(), output);
                return output;
            }

        }
    }

    static Operand<TFloat32> dt(Ops tf, Operand<TFloat32> img, long n, int axis) {
        List<Operand<TFloat32>> stack = new ArrayList<>();
//        long pMax = img.shape().get(1 - axis);
//        long qMax = img.shape().get(axis);
//        Operand<TFloat32> pIndices = tf.expandDims(
//                tf.range(tf.constant(0f), tf.constant((float)pMax), tf.constant(1f)), // [pMax]
//                tf.constant(axis)
//        ); // [pMax, 1]
//        Operand<TInt64> sz = tf.constant(new long[]{-1L, -1L});
        Operand<TFloat32> df = tf.deepCopy(img);
        Operand<TInt64> sliceSz = tf.constant(new long[] {
                axis == 0 ? 1L : -1,
                axis == 1 ? 1L : -1,
        });
        Operand<TFloat32> prevDfq = tf.slice(
                df,
                tf.constant(new long[] {0L, 0L}),
                sliceSz
        );
        LOG.info("!!!! D({}): {}", 0, tensorToString(prevDfq));
        stack.add(prevDfq);
        for (long q = 1; q < n ; q++) {
            Operand<TFloat32> dfq = tf.slice(
                    df,
                    tf.constant(new long[]{
                            axis == 0 ? q : 0L,
                            axis == 1 ? q : 0L,
                    }),
                    sliceSz
            );
            Operand<TFloat32> d1 = tf.math.add(prevDfq, tf.constant(1f));
            Operand<TFloat32> d = tf.select(tf.math.lessEqual(tf.math.sub(dfq, d1), tf.constant(0f)), dfq, d1);
            LOG.info("!!!! D({}): {}", q, tensorToString(dfq));
            LOG.info("!!!! D({}): {}", q - 1, tensorToString(prevDfq));
            LOG.info("!!!! D({})+1: {}", q - 1, tensorToString(d1));
            LOG.info("!!!! SELECTED D({}): {}", q, tensorToString(d));
            stack.add(d);
            prevDfq = d;
        }
        df = tf.concat(stack, tf.constant(axis));
        LOG.info("!!!! 1st iter DF: {}", tensorToString(df));
        stack.clear();
        stack.add(prevDfq);
        for (long q = n - 2; q >= 0; q--) {
            Operand<TFloat32> dfq = tf.slice(
                    df,
                    tf.constant(new long[]{
                            axis == 0 ? q : 0L,
                            axis == 1 ? q : 0L,
                    }),
                    sliceSz
            );
            Operand<TFloat32> d1 = tf.math.add(prevDfq, tf.constant(1f));
            Operand<TFloat32> d = tf.select(tf.math.lessEqual(tf.math.sub(dfq, d1), tf.constant(0f)), dfq, d1);
            LOG.info("!!!! D({}): {}", q, tensorToString(dfq));
            LOG.info("!!!! D({}): {}", q + 1, tensorToString(prevDfq));
            LOG.info("!!!! D({})+1: {}", q + 1, tensorToString(d1));
            LOG.info("!!!! SELECTED D({}): {}", q, tensorToString(d));
            stack.add(d);
            prevDfq = d;
//            Operand<TFloat32> beforeQFs = tf.slice(img,
//                    tf.constant(new long[] {0, 0}),
//                    tf.constant(new long[] {
//                            axis == 0 ? q : pMax,
//                            axis == 1 ? q : pMax
//                    }));
//            Operand<TFloat32> beforeQIndices = tf.expandDims(
//                    tf.range(tf.constant(0f),
//                            tf.constant((float)(q)), tf.constant(1f)), // [q]
//                    tf.constant(1-axis)
//            ); // [1, q]
//            Operand<TFloat32> beforeQDist = tf.math.square(tf.math.sub(beforeQIndices, pIndices));
//            Operand<TFloat32> beforeQFPlusDist = tf.math.add(beforeQFs, beforeQDist);
//
//            Operand<TInt64> qSlice = tf.constant(
//                    new long[]{
//                            axis == 0 ? q+1 : 0L,
//                            axis == 1 ? q+1 : 0L,
//                    }
//            );
//            Operand<TFloat32> afterQFs = tf.slice(img, qSlice, sz);
//            Operand<TFloat32> afterQIndices = tf.expandDims(
//                    tf.range(tf.constant(0f),
//                            tf.constant((float)(qMax - q - 1)), tf.constant(1f)), // [qMax - q]
//                    tf.constant(1-axis)
//            ); // [1, height - q]
//            // Compute f(p) + (p - q)^2 for all p, q
//            Operand<TFloat32> afterQDist = tf.math.square(tf.math.sub(afterQIndices, pIndices));
//            Operand<TFloat32> afterQFPlusDist = tf.math.add(afterQFs, afterQDist);
//            Operand<TFloat32> fPlusDist = tf.concat(Arrays.asList(beforeQFPlusDist, afterQFPlusDist), tf.constant(axis));
//            Operand<TFloat32> d = tf.min(fPlusDist, tf.constant(axis), Min.keepDims(true));
        }
        df = tf.concat(stack, tf.constant(axis));
        LOG.info("!!!! 2nd iter DF: {}", tensorToString(df));
        return df;
    }

    private static String tensorToString(Operand<TFloat32> t) {
        String[] fa = new String[(int)t.shape().get(0)];
        for (int j=0; j < fa.length; j++) {
            float[] faj = new float[(int) t.shape().get(1)];
            for (int i=0; i < faj.length; i++) {
                faj[i] = t.asTensor().getFloat(j, i);
            }
            fa[j] = Arrays.toString(faj) + "\n";
        }
        return Arrays.toString(fa);
    }

}
