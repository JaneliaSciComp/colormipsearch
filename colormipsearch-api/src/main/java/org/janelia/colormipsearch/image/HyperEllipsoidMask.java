package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperEllipsoidMask extends HyperEllipsoidRegion{

    final boolean[] kernelMask;
    private final RectIntervalHelper kernelIntervalHelper;
    private final int[] tmpDist;

    public HyperEllipsoidMask(int... radii) {
        super(radii);
        this.kernelIntervalHelper = new RectIntervalHelper(Arrays.stream(radii).map(d -> d+1).toArray());
        this.kernelMask = createKernel();
        tmpDist = new int[numDimensions];
    }

    private HyperEllipsoidMask(HyperEllipsoidMask c) {
        super(c);
        this.kernelIntervalHelper = c.kernelIntervalHelper;
        this.kernelMask = c.kernelMask.clone();
        tmpDist = c.tmpDist.clone();
    }

    HyperEllipsoidRegion copy() {
        return new HyperEllipsoidRegion(this);
    }

    private boolean[] createKernel() {
        boolean[] mask = new boolean[(int) kernelIntervalHelper.getSize()];
        for (int i = 0; i < mask.length; i++) {
            int[] currentRs = kernelIntervalHelper.intLinearIndexToRectCoords(i);
            if (checkEllipsoidEquation(currentRs)) {
                mask[i] = true;
            }
        }
        return mask;
    }

    public boolean[] getKernelMask() {
        return kernelMask;
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
        int maskIndex = kernelIntervalHelper.rectCoordsToIntLinearIndex(distance);
        return maskIndex < kernelMask.length && kernelMask[maskIndex];
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
