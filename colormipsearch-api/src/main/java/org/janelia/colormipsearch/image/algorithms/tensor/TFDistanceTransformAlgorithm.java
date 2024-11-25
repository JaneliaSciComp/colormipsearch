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
import org.tensorflow.Graph;
import org.tensorflow.Operand;
import org.tensorflow.Session;
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
        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());

            Operand<TFloat32> tNdInput = tf.dtypes.cast(
                    tf.constant(ndInput),
                    TFloat32.class
            );
            Operand<TFloat32> f = tf.select(tf.math.greater(tNdInput, tf.constant(0.f)),
                    tf.constant(0.f),
                    tf.constant(Float.MAX_VALUE));
            LOG.info("F.shape: {}", f.shape());
            Operand<TFloat32> dty = compute1d(tf, f, f.shape().get(0), 0);
            Operand<TFloat32> dtx = compute2d(tf, dty, f.shape().get(1), 1);
            Operand<TInt32> ndOutput = tf.dtypes.cast(dtx, TInt32.class);
            try (Session session = new Session(execEnv);
                 Tensor result = session.runner().fetch(ndOutput).run().get(0)) {
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

    static Operand<TFloat32> compute1d(Ops tf, Operand<TFloat32> img, long n, int axis) {
        List<Operand<TFloat32>> stack = new ArrayList<>();
        Operand<TFloat32> df = img;
        Operand<TInt64> sliceSz = tf.constant(new long[] {
                axis == 0 ? 1L : -1,
                axis == 1 ? 1L : -1,
        });
        Operand<TFloat32> d0 = tf.slice(
                df,
                tf.constant(new long[] {0L, 0L}),
                sliceSz
        );
        stack.add(d0);
        Operand<TFloat32> distanceStep = tf.constant(1f);
        for (long q = 1; q < n ; q++) {
            Operand<TFloat32> dfq = tf.slice(
                    df,
                    tf.constant(new long[]{
                            axis == 0 ? q : 0L,
                            axis == 1 ? q : 0L,
                    }),
                    sliceSz
            );
            Operand<TFloat32> prevDfq = stack.get(stack.size()-1);
            Operand<TFloat32> d1 = tf.math.add(prevDfq, distanceStep);
            Operand<TFloat32> nextDistanceStep = tf.math.add(distanceStep, tf.constant(2f));

            Operand<TFloat32> d = tf.select(tf.math.greater(tf.math.sub(dfq, d1), tf.constant(0f)), d1, dfq);
            distanceStep = tf.select(tf.math.greater(tf.math.sub(dfq, d1), tf.constant(0f)), nextDistanceStep, tf.constant(1f));
            stack.add(d);
        }
        df = tf.concat(stack, tf.constant(axis));
        Operand<TFloat32> dn = stack.get(stack.size()-1);
        stack.clear();
        stack.add(dn);
        distanceStep = tf.constant(1f);
        for (long q = n - 2; q >= 0; q--) {
            Operand<TFloat32> dfq = tf.slice(
                    df,
                    tf.constant(new long[]{
                            axis == 0 ? q : 0L,
                            axis == 1 ? q : 0L,
                    }),
                    sliceSz
            );
            // we are going backwards so the next slice is always the first
            Operand<TFloat32> nextDfq = stack.get(0);
            Operand<TFloat32> d1 = tf.math.add(nextDfq, distanceStep);
            Operand<TFloat32> nextDistanceStep = tf.math.add(distanceStep, tf.constant(2f));

            Operand<TFloat32> d = tf.select(tf.math.greater(tf.math.sub(dfq, d1), tf.constant(0f)), d1, dfq);
            distanceStep = tf.select(tf.math.greater(tf.math.sub(dfq, d1), tf.constant(0f)), nextDistanceStep, tf.constant(1f));
            // we are going backwards so insert at the top
            stack.add(0, d);
        }
        df = tf.concat(stack, tf.constant(axis));
        LOG.info("Compute 1d by {} -> {}", axis, df.shape());
        return df;
    }

    static Operand<TFloat32> compute2d(Ops tf, Operand<TFloat32> f, long n, int axis) {
        // compute the distance matrix for all points: M[i,j]=(i-j)*(i-j)
        Operand<TFloat32> squareDists = tf.math.square(
                tf.math.sub(
                        tf.expandDims(
                                tf.range(tf.constant(0f),tf.constant((float)n), tf.constant(1f)),
                                tf.constant(0)
                        ),
                        tf.expandDims(
                                tf.range(tf.constant(0f),tf.constant((float)n), tf.constant(1f)),
                                tf.constant(1)
                        )
                )
        );
        List<Operand<TFloat32>> stack = new ArrayList<>();
        for (long q = 0; q < n; q++) {
            Operand<TFloat32> dfq = tf.slice(
                    squareDists,
                    tf.constant(new long[]{
                            axis == 0 ? 0L : q,
                            axis == 1 ? 0L : q,
                    }),
                    tf.constant(new long[] {
                            axis == 0 ? -1L : 1L,
                            axis == 1 ? -1L : 1L,
                    })
            );
            Operand<TFloat32> squareD = tf.math.add(f, dfq);
            Operand<TFloat32> minSquareD = tf.min(
                    squareD,
                    tf.constant(axis),
                    Min.keepDims(true)
            );
            stack.add(tf.math.sqrt(minSquareD));
        }
        return tf.concat(stack, tf.constant(axis));
    }

}
