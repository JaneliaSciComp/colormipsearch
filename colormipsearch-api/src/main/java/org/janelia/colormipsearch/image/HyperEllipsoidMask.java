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


    public int[] getKernelMask(int inChannels, int outChannels) {
        RectIntervalHelper kernelIntervalHelper = new RectIntervalHelper(Arrays.stream(radii).map(d -> 2*d+1).toArray());

        int maskSize = (int) kernelIntervalHelper.getSize();
        int[] mask = new int[inChannels * outChannels * maskSize];
        int[] currentRs = new int[numDimensions];
        for (int i = 0; i < maskSize; i++) {
            int[] currentIndexes = kernelIntervalHelper.intLinearIndexToRectCoords(i);
            for (int d = 0; d < numDimensions; d++) {
                currentRs[d] = currentIndexes[d] - radii[d];
            }
            int maskValue = checkEllipsoidEquation(currentRs) ? 1 : 0;
            for (int cout = 0; cout < outChannels; cout++) {
                for (int cin = 0; cin < inChannels; cin++) {
                    if (cout == cin) {
                        mask[(cout * inChannels + cin) * maskSize + i] = maskValue;
                    } else {
                        mask[(cout * inChannels + cin) * maskSize + i] = 0;
                    }
                }
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
