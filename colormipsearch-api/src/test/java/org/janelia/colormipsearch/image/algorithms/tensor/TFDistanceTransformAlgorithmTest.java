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
        float[][] img = new float[][]{
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
                {0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
        };
        try (Graph execEnv = TensorflowUtils.createExecutionGraph()) {
            Ops tf = Ops.create(execEnv).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.CPU).build());
            Operand<TFloat32> timg = tf.constant(img);
            Operand<TFloat32> f = tf.select(tf.math.greater(timg, tf.constant(0.f)),
                    tf.constant(0.f),
                    tf.constant(Float.MAX_VALUE));

            Operand<TFloat32> dty = TFDistanceTransformAlgorithm.compute1d(tf, f, timg.shape().get(0), 0);
            Operand<TFloat32> dtx = TFDistanceTransformAlgorithm.compute2d(tf, dty, timg.shape().get(1), 1);
//            try(Tensor timgResult = timg.asTensor();
//                 Tensor dtyResult = dty.asTensor();
//                 Tensor dtxResult = dtx.asTensor();
//                ) {
//                 LOG.info("!!!! timg {} -> {}", timg.shape(), tensorToString(timgResult));
//                 LOG.info("!!!! DTY {} -> {}", dty.shape(), tensorToString(dtyResult));
//                 LOG.info("!!!! DTX {} -> {}", dtx.shape(), tensorToString(dtxResult));
//            }

            try (Session s = new Session(execEnv);
                 Tensor timgResult = s.runner().fetch(timg).run().get(0);
                 Tensor dtyResult = s.runner().fetch(dty).run().get(0);
                 Tensor dtxResult = s.runner().fetch(dtx).run().get(0);
                 ) {
                 LOG.info("!!!! timg {} -> {}", timg.shape(), tensorToString(timgResult));
                 LOG.info("!!!! DTY {} -> {}", dty.shape(), tensorToString(dtyResult));
                 LOG.info("!!!! DTX {} -> {}", dtx.shape(), tensorToString(dtxResult));
            }
        }
    }

    private String tensorToString(Tensor t) {
        String[] fa = new String[(int)t.shape().get(0)];
        for (int j=0; j < fa.length; j++) {
            float[] faj = new float[(int) t.shape().get(1)];
            for (int i=0; i < faj.length; i++) {
                faj[i] = t.asRawTensor().asRawTensor().data().asFloats().getFloat(j*faj.length+i);
            }
            fa[j] = Arrays.toString(faj) + "\n";
        }
        return Arrays.toString(fa);
    }
}
