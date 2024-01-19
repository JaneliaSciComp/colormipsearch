package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.janelia.colormipsearch.image.GeomTransform;

public class MirrorTransform implements GeomTransform {
    private final long[] min;
    private final long[] max;
    private final int ndims;
    private final int axis;

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
    public long[] apply(long[] coords) {
        return IntStream
                .range(0, ndims)
                .mapToLong(a -> {
                    if (a == axis) {
                        return min[a] + max[a] - coords[a];
                    } else {
                        return coords[a];
                    }
                })
                .toArray();
    }
}
