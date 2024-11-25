package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.Arrays;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.DeviceSpec;
import org.tensorflow.EagerSession;
import org.tensorflow.Graph;
import org.tensorflow.Operand;
import org.tensorflow.Result;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.op.Ops;
import org.tensorflow.types.TFloat32;

public class TFDistanceTransformAlgorithmTest {
    private static final Logger LOG = LoggerFactory.getLogger(TFDistanceTransformAlgorithmTest.class);

    @Test
    public void dt() {
        class TestData {
            final float[][] img;

            TestData(float[][] img) {
                this.img = img;
            }
        }
        TestData[] testData = new TestData[] {
//                new TestData(new float[][]{
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
//                        {0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
//                        {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
//                }),
                new TestData(new float[][]{
                        {0f, 0f, 0f},
                        {1f, 0f, 0f},
                        {0f, 0f, 0f},

                })
        };
        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.CPU).build());
            for (TestData td : testData) {
                Operand<TFloat32> timg = tf.constant(td.img);
                Operand<TFloat32> f = tf.select(tf.math.greater(timg, tf.constant(0.f)),
                        tf.constant(0.f),
                        tf.constant(Float.MAX_VALUE));
                Operand<TFloat32> dty = TFDistanceTransformAlgorithm.compute1d(tf, f, timg.shape().get(1), 1);
                Operand<TFloat32> dtx = TFDistanceTransformAlgorithm.compute2d(tf, dty, timg.shape().get(0), 0);
                try (Session s = new Session(execEnv)) {
                    logResultsUsingGraphSession("DTY", dty, s);
                    logResultsUsingGraphSession("DTX", dtx, s);
                }
            }

        }
    }

    private void logResultsUsingEagerSession(String resultName, Operand<TFloat32> o) {
        try (Tensor t = o.asTensor()) {
            LOG.info("{} {} -> {}", resultName, t.shape(), tensorToString(t));
        }
    }

    private void logResultsUsingGraphSession(String resultName, Operand<TFloat32> o, Session s) {
        try (Tensor result = s.runner().fetch(o).run().get(0)) {
            LOG.info("{} {} -> {}", resultName, result.shape(), tensorToString(result));
        }
    }

    private String tensorToString(Tensor t) {
        String[] fa = new String[(int) t.shape().get(0)];
        for (int j = 0; j < fa.length; j++) {
            float[] faj = new float[(int) t.shape().get(1)];
            for (int i = 0; i < faj.length; i++) {
                faj[i] = t.asRawTensor().asRawTensor().data().asFloats().getFloat(j * faj.length + i);
            }
            fa[j] = Arrays.toString(faj) + "\n";
        }
        return Arrays.toString(fa);
    }
}
