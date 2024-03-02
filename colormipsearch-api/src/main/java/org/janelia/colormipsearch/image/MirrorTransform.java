package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class MirrorTransform implements GeomTransform {
    private final long[] min;
    private final long[] max;
    private final int ndims;
    private final int axis;

    public MirrorTransform(long[] shape, int axis) {
        this(ImageAccessUtils.getZero(shape.length), ImageAccessUtils.getMax(shape), axis);
    }

    public MirrorTransform(long[] min, long[] max, int axis) {
        assert min.length == max.length;
        this.min = Arrays.copyOf(min, min.length);
        this.max = Arrays.copyOf(max, max.length);
        this.ndims = min.length;
        this.axis = axis;
    }

    @Override
    public int getSourceDims() {
        return ndims;
    }

    @Override
    public int getTargetDims() {
        return ndims;
    }

    @Override
    public void apply(long[] source, long[] target) {
        System.arraycopy(source, 0, target, 0, source.length);
        target[axis] = min[axis] + max[axis] - source[axis];
    }
}
