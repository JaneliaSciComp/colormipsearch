package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.stream.IntStream;

public class ShiftTransform implements GeomTransform {
    private final long[] shifts;

    public ShiftTransform(long[] shifts) {
        this.shifts = Arrays.copyOf(shifts, shifts.length);
    }

    @Override
    public int getSourceDims() {
        return shifts.length;
    }

    @Override
    public int getTargetDims() {
        return shifts.length;
    }

    @Override
    public void apply(long[] source, long[] target) {
        for (int d = 0; d < shifts.length; d++) {
            target[d] = source[d] + shifts[d];
        }
    }
}
