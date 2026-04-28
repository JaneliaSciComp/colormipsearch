package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class HyperEllipsoidMask {

    private final int numDimensions;
    private final int[] radii;

    public HyperEllipsoidMask(int... radii) {
        this.numDimensions = radii.length;
        this.radii = radii.clone();
    }

    public int[] getRadii() {
        return radii;
    }

    public boolean contains(int... distance) {
        double dist = 0;
        for (int d = 0; d < distance.length; d++) {
            double delta = distance[d];
            dist += (delta * delta) / ((double) radii[d] * radii[d]);
        }
        return dist <= 1;
    }

    public int[] getKernelDims() {
        return Arrays.stream(radii).map(r -> 2 * r + 1).toArray();
    }
}
