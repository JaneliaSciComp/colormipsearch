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
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.For;
import org.tensorflow.op.core.Min;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

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
            Operand<TFloat32> dty = dtAlong1stAxis(tf, f, f.shape(), 1);
            Operand<TFloat32> dtx = dtAlong2ndAxis(tf, dty, f.shape(), 0);
            Operand<TInt32> ndOutput = tf.dtypes.cast(dtx, TInt32.class);
            try (Session session = TensorflowUtils.createSession(execEnv);
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

    static Operand<TFloat32> dtAlong1stAxis(Ops tf, Operand<TFloat32> img, Shape s, int axis) {
        Operand<TInt32> slicePos = tf.constant(new int[]{0, 0});
        Operand<TInt32> sliceSz = tf.constant(new int[]{
                axis == 0 ? 1 : -1,
                axis == 1 ? 1 : -1,
        });
        Operand<TInt32> sliceInc = tf.constant(new int[]{
                axis == 0 ? 1 : 0,
                axis == 1 ? 1 : 0,
        });
        Operand<TFloat32> prevDfq = tf.slice(
                img,
                tf.constant(new int[]{0, 0}),
                sliceSz
        );
        Operand<TFloat32> distanceStep = tf.constant(1f);
        ConcreteFunction forwardLoopBody = ConcreteFunction.create(
                (Ops tfOps) -> {
                    Operand<TInt32> loopIndex = tfOps.placeholder(TInt32.class); // this is passed implicitly by tensorflow
                    Operand<TFloat32> dfInput = tfOps.placeholder(TFloat32.class);
                    Operand<TFloat32> dfResultInput = tfOps.placeholder(TFloat32.class);
                    Operand<TFloat32> prevDfqInput = tfOps.placeholder(TFloat32.class);
                    Operand<TInt32> slicePosInput = tfOps.placeholder(TInt32.class);
                    Operand<TInt32> sliceIncInput = tfOps.placeholder(TInt32.class);
                    Operand<TInt32> sliceSzInput = tfOps.placeholder(TInt32.class);
                    Operand<TFloat32> distanceStepInput = tfOps.placeholder(TFloat32.class);

                    Operand<TInt32> currentSlicePos = tfOps.math.add(slicePosInput, sliceIncInput);
                    Operand<TFloat32> dfq = tfOps.slice(dfInput, currentSlicePos, sliceSzInput);
                    Operand<TFloat32> d = tfOps.math.add(prevDfqInput, distanceStepInput);
                    Operand<TFloat32> minD = tfOps.select(
                            tfOps.math.greater(dfq, d),
                            d,
                            dfq
                    );
                    Operand<TFloat32> nextDistanceStep = tfOps.select(
                            tfOps.math.greater(dfq, d),
                            tfOps.math.add(distanceStepInput, tfOps.constant(2f)),
                            tfOps.constant(1f)
                    );
                    Operand<TFloat32> nextDfResult = tfOps.concat(Arrays.asList(dfResultInput, minD), tfOps.constant(axis));
                    return Signature.builder()
                            .input("loopIndex", loopIndex)
                            .input("df", dfInput)
                            .input("dfResult", dfResultInput)
                            .input("prevDfq", prevDfqInput)
                            .input("slicePos", slicePosInput)
                            .input("sliceInc", sliceIncInput)
                            .input("sliceSz", sliceSzInput)
                            .input("distanceStep", distanceStepInput)
                            .output("df", dfInput)
                            .output("dfResult", nextDfResult)
                            .output("prevDfq", minD)
                            .output("slicePos", currentSlicePos)
                            .output("sliceInc", sliceIncInput)
                            .output("sliceSz", sliceSzInput)
                            .output("distanceStep", nextDistanceStep)
                            .build();
                }
        );
        For forwardLoop = tf.forOp(
                tf.constant(1),
                tf.constant((int) s.get(axis)),
                tf.constant(1),
                Arrays.asList(img, prevDfq/* initial result*/, prevDfq/*first slice*/, slicePos, sliceInc, sliceSz, distanceStep),
                forwardLoopBody
        );
        List<Operand<?>> forwardLoopResults = new ArrayList<>();
        forwardLoop.forEach(forwardLoopResults::add);

        ConcreteFunction reverseLoopBody = ConcreteFunction.create(
                (Ops tfOps) -> {
                    Operand<TInt32> loopIndex = tfOps.placeholder(TInt32.class); // this is passed implicitly by tensorflow
                    Operand<TFloat32> dfInput = tfOps.placeholder(TFloat32.class);
                    Operand<TFloat32> dfResultInput = tfOps.placeholder(TFloat32.class);
                    Operand<TFloat32> prevDfqInput = tfOps.placeholder(TFloat32.class);
                    Operand<TInt32> slicePosInput = tfOps.placeholder(TInt32.class);
                    Operand<TInt32> sliceIncInput = tfOps.placeholder(TInt32.class);
                    Operand<TInt32> sliceSzInput = tfOps.placeholder(TInt32.class);
                    Operand<TFloat32> distanceStepInput = tfOps.placeholder(TFloat32.class);

                    Operand<TInt32> currentSlicePos = tfOps.math.sub(slicePosInput, sliceIncInput); // subtract this time
                    Operand<TFloat32> dfq = tfOps.slice(dfInput, currentSlicePos, sliceSzInput);
                    Operand<TFloat32> d = tfOps.math.add(prevDfqInput, distanceStepInput);
                    Operand<TFloat32> minD = tfOps.select(
                            tfOps.math.greater(dfq, d),
                            d,
                            dfq
                    );
                    Operand<TFloat32> nextDistanceStep = tfOps.select(
                            tfOps.math.greater(dfq, d),
                            tfOps.math.add(distanceStepInput, tfOps.constant(2f)),
                            tfOps.constant(1f)
                    );
                    Operand<TFloat32> nextDfResult = tfOps.concat(Arrays.asList(minD, dfResultInput), tfOps.constant(axis));
                    return Signature.builder()
                            .input("loopIndex", loopIndex)
                            .input("df", dfInput)
                            .input("dfResult", dfResultInput)
                            .input("prevDfq", prevDfqInput)
                            .input("slicePos", slicePosInput)
                            .input("sliceInc", sliceIncInput)
                            .input("sliceSz", sliceSzInput)
                            .input("distanceStep", distanceStepInput)
                            .output("df", dfInput)
                            .output("dfResult", nextDfResult)
                            .output("prevDfq", minD)
                            .output("slicePos", currentSlicePos)
                            .output("sliceInc", sliceIncInput)
                            .output("sliceSz", sliceSzInput)
                            .output("distanceStep", nextDistanceStep)
                            .build();
                }
        );
        For reverseLoop = tf.forOp(
                tf.constant((int) s.get(axis) - 2),
                tf.constant((int) -1),
                tf.constant(-1),
                Arrays.asList(
                        forwardLoopResults.get(1), // input f is the dfResult from the forward iteration
                        forwardLoopResults.get(2), // initial result is last slice from forward iteration
                        forwardLoopResults.get(2), // prevSlice is last slice from forward iteration
                        forwardLoopResults.get(3), // current slice position is the last slice pos from the forward iteration
                        sliceInc,
                        sliceSz,
                        distanceStep),
                reverseLoopBody
        );
        LOG.info("Compute 1d by {} -> {}", axis, img.shape());
        return (Operand<TFloat32>) reverseLoop.output().get(1); // return only the result distance transform
    }

    static Operand<TFloat32> dtAlong2ndAxis(Ops tf, Operand<TFloat32> f, Shape s, int axis) {
        // compute the distance matrix for all points: M[i,j]=(i-j)*(i-j)
//        Operand<TFloat32> squareDists = tf.math.square(
//                tf.math.sub(
//                        tf.expandDims(
//                                tf.range(tf.constant(0f), tf.constant((float) n), tf.constant(1f)),
//                                tf.constant(0)
//                        ),
//                        tf.expandDims(
//                                tf.range(tf.constant(0f), tf.constant((float) n), tf.constant(1f)),
//                                tf.constant(1)
//                        )
//                )
//        );
        ConcreteFunction loopBody = ConcreteFunction.create(
                (Ops tfOps) -> {
                    Operand<TInt32> loopIndex = tfOps.placeholder(TInt32.class); // this is passed implicitly by tensorflow
                    Operand<TFloat32> fInput = tfOps.placeholder(TFloat32.class);
                    Operand<TFloat32> fResultInput = tfOps.placeholder(TFloat32.class);

                    Operand<TFloat32> dfq = tfOps.math.square(
                            tfOps.math.sub(
                                    tfOps.expandDims(
                                            tfOps.range(tfOps.constant(0f), tfOps.constant((float) s.get(axis)), tfOps.constant(1f)),
                                            tfOps.constant(1 - axis)
                                    ),
                                    tfOps.dtypes.cast(loopIndex, TFloat32.class)
                            )
                    );
                    Operand<TFloat32> squareD = tfOps.math.add(fInput, dfq);
                    Operand<TFloat32> minSquareD = tfOps.math.sqrt(
                            tfOps.min(
                                    squareD,
                                    tfOps.constant(axis),
                                    Min.keepDims(true)
                            ));
                    Operand<TFloat32> fResult = tfOps.concat(Arrays.asList(fResultInput, minSquareD), tfOps.constant(axis));
                    return Signature.builder()
                            .input("loopIndex", loopIndex)
                            .input("f", fInput)
                            .input("fResult", fResultInput)
                            .output("f", fInput)
                            .output("fResult", fResult)
                            .build();
                }
        );
        Operand<TFloat32> initialResult = tf.empty(
                tf.constant(new int[]{
                        axis == 0 ? 0 : (int) s.get(0),
                        axis == 1 ? 0 : (int) s.get(1),
                }),
                TFloat32.class
        );
        For loop = tf.forOp(
                tf.constant(0),
                tf.constant((int) s.get(axis)),
                tf.constant(1),
                Arrays.asList(f, initialResult),
                loopBody
        );
//        List<Operand<TFloat32>> stack = new ArrayList<>();
//        for (long q = 0; q < n; q++) {
//            Operand<TFloat32> dfq = tf.slice(
//                    squareDists,
//                    tf.constant(new int[]{
//                            axis == 0 ? 0 : (int) q,
//                            axis == 1 ? 0 : (int) q,
//                    }),
//                    tf.constant(new int[]{
//                            axis == 0 ? -1 : 1,
//                            axis == 1 ? -1 : 1,
//                    })
//            );
//            Operand<TFloat32> squareD = tf.math.add(f, dfq);
//            Operand<TFloat32> minSquareD = tf.min(
//                    squareD,
//                    tf.constant(axis),
//                    Min.keepDims(true)
//            );
//            stack.add(tf.math.sqrt(minSquareD));
//        }
//        return tf.concat(stack, tf.constant(axis));
        return (Operand<TFloat32>) loop.output().get(1); // return only the result distance

    }

}
