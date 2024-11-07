package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperEllipsoidMask extends HyperEllipsoidRegion{

//    final int[] kernelMask;
//    private final RectIntervalHelper kernelIntervalHelper;
    private final int[] tmpDist;

    public HyperEllipsoidMask(int... radii) {
        super(radii);
        tmpDist = new int[numDimensions];
    }

    private HyperEllipsoidMask(HyperEllipsoidMask c) {
        super(c);
//        this.kernelIntervalHelper = c.kernelIntervalHelper;
//        this.kernelMask = c.kernelMask.clone();
        tmpDist = c.tmpDist.clone();
    }

    HyperEllipsoidRegion copy() {
        return new HyperEllipsoidRegion(this);
    }

    public int[] getKernelMask() {
        RectIntervalHelper kernelIntervalHelper = new RectIntervalHelper(Arrays.stream(radii).map(d -> 2*d+1).toArray());

        int[] mask = new int[(int) kernelIntervalHelper.getSize()];
//        for (int rz = 0; rz < radii[0]; rz++) {
//            for (int ry = 0; ry < radii[1]; ry++) {
//                for (int rx = 0; rx < radii[2]; rx++) {
//                    if (checkEllipsoidEquation(rz, ry, rx)) {
//                        int[] coords1 = {radii[0] + rz, radii[1] + ry, radii[2] + rx};
//                        int[] coords2 = {radii[0] + rz, radii[1] + ry, radii[2] - rx};
//                        int[] coords3 = {radii[0] + rz, radii[1] - ry, radii[2] + rx};
//                        int[] coords4 = {radii[0] + rz, radii[1] - ry, radii[2] - rx};
//                        int[] coords5 = {radii[0] - rz, radii[1] + ry, radii[2] + rx};
//                        int[] coords6 = {radii[0] - rz, radii[1] + ry, radii[2] - rx};
//                        int[] coords7 = {radii[0] - rz, radii[1] - ry, radii[2] + rx};
//                        int[] coords8 = {radii[0] - rz, radii[1] - ry, radii[2] - rx};
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords1)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords2)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords3)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords4)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords5)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords6)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords7)] = 1;
//                        mask[kernelIntervalHelper.rectCoordsToIntLinearIndex(coords8)] = 1;
//                    }
//                }
//            }
//        }
        int[] currentRs = new int[numDimensions];
        for (int i = 0; i < mask.length; i++) {
            int[] currentIndexes = kernelIntervalHelper.intLinearIndexToRectCoords(i);
            for (int d = 0; d < numDimensions; d++) {
                currentRs[d] = currentIndexes[d] - radii[d];
            }
            if (checkEllipsoidEquation(currentRs)) {
                mask[i] = 1;
            } else {
                mask[i] = 0;
            }
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
