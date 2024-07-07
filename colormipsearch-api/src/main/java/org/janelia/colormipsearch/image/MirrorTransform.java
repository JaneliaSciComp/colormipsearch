package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class MirrorTransform implements GeomTransform {
    private final long[] min;
    private final long[] max;
    private final int axis;

    public MirrorTransform(long[] shape, int axis) {
        this(ImageAccessUtils.getZero(shape.length), ImageAccessUtils.getMax(shape), axis);
    }

    public MirrorTransform(long[] min, long[] max, int axis) {
        assert min.length == max.length;
        this.min = Arrays.copyOf(min, min.length);
        this.max = Arrays.copyOf(max, max.length);
        this.axis = axis;
    }

    @Override
    public void apply(long[] currentPos, long[] originPos) {
        System.arraycopy(currentPos, 0, originPos, 0, currentPos.length);
        originPos[axis] = min[axis] + max[axis] - currentPos[axis];
    }
}
