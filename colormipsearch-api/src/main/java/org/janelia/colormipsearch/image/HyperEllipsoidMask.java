package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class HyperEllipsoidMask extends HyperEllipsoidRegion{

    private final int[] tmpDist;

    public HyperEllipsoidMask(int... radii) {
        super(radii);
        tmpDist = new int[numDimensions];
    }

    public int[] getKernelMask() {
        RectIntervalHelper kernelIntervalHelper = new RectIntervalHelper(Arrays.stream(radii).map(d -> 2*d+1).toArray());
        int maskSize = (int) kernelIntervalHelper.getSize();
        int[] mask = new int[maskSize];
        int[] currentRs = new int[numDimensions];
        for (int i = 0; i < maskSize; i++) {
            int[] currentIndexes = kernelIntervalHelper.intLinearIndexToRectCoords(i);
            for (int d = 0; d < numDimensions; d++) {
                currentRs[d] = currentIndexes[d] - radii[d];
            }
            int maskValue = checkEllipsoidEquation(currentRs) ? 1 : 0;
            mask[i] = maskValue;
        }
        return mask;
    }

    private boolean checkEllipsoidEquation(int... rs) {
        double dist = 0;
        for (int d = 0; d < rs.length; d++) {
            double delta = rs[d];
            dist += (delta * delta) / (radii[d] * radii[d]);
        }
        return dist <= 1;
    }

    /**
     *
     * @param distance distance from the center - must be positive
     */
    public boolean contains(int... distance) {
        return checkEllipsoidEquation(distance);
    }

    @Override
    public boolean containsLocation(long[] p) {
        for (int d = 0; d < tmpDist.length; d++) {
            tmpDist[d] = (int)Math.abs(p[d] - center[d]);
            if (tmpDist[d] > radii[d]) {
                return false;
            }
        }
        return contains(tmpDist);
    }
}
