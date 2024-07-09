package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class ScaleTransform implements GeomTransform {
    private final double[] scaleFactors;

    public ScaleTransform(double[] scaleFactors) {
        this.scaleFactors = Arrays.copyOf(scaleFactors, scaleFactors.length);
    }

    @Override
    public void apply(long[] currentPos, long[] originPos) {
        for (int d = 0; d < scaleFactors.length; d++) {
            originPos[d] = (long)(currentPos[d] * scaleFactors[d]);
        }
    }
}
