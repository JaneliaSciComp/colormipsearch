package org.janelia.colormipsearch.image.algorithms.tensor;

import net.imglib2.RandomAccessibleInterval;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.tensorflow.DeviceSpec;
import org.tensorflow.EagerSession;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.op.Ops;

public class TFDistanceTransform {
    public static <T extends RGBPixelType<T>> void distanceTransform2DRGB(RandomAccessibleInterval<? extends RGBPixelType<?>> input, String deviceName) {
        long startTime = System.currentTimeMillis();
        Shape inputShape = Shape.of(input.dimension(2), input.dimension(1), input.dimension(0));

        try (EagerSession eagerSession = TensorflowUtils.createEagerSession()) {
            Ops tf = Ops.create(eagerSession).withDevice(DeviceSpec.newBuilder().deviceType(DeviceSpec.DeviceType.valueOf(deviceName.toUpperCase())).build());
            // TODO: implement distance transform
        }
    }
}
