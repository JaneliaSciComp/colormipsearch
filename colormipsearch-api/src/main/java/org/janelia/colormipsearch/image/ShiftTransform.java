package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.stream.IntStream;

public class ShiftTransform implements GeomTransform {
    private final long[] shifts;

    public ShiftTransform(long[] shifts) {
        this.shifts = Arrays.copyOf(shifts, shifts.length);
    }

    @Override
    public void apply(long[] currentPos, long[] originPos) {
        for (int d = 0; d < shifts.length; d++) {
            originPos[d] = currentPos[d] + shifts[d];
        }
    }
}
