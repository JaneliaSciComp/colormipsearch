package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperEllipsoidRegion {

    public interface SegmentProcessor {
        /**
         * Process pixels on the segment from the given center position left and right along the axis
         * up to the specified distance.
         *
         * @param centerCoords
         * @param distance
         * @param axis
         * @returnn number of pixes processed
         */
        long processSegment(long[] centerCoords, int distance, int axis);
    }

    final int[] radii;
    // region center
    final long[] center;
    // region boundaries
    final long[] min;
    final long[] max;
    final long[] tmpCoords;
    final boolean[] kernelMask;
    private final RectIntervalHelper kernelIntervalHelper;

    public HyperEllipsoidRegion(int... radii) {
        this.radii = radii;
        this.center = new long[numDimensions()];
        this.min = new long[numDimensions()];
        this.max = new long[numDimensions()];
        this.tmpCoords = new long[numDimensions()];
        this.kernelIntervalHelper = new RectIntervalHelper(Arrays.stream(radii).map(d -> d+1).toArray());
        this.kernelMask = createKernel(radii);
    }

    private HyperEllipsoidRegion(HyperEllipsoidRegion c) {
        this.radii = c.radii;
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
        this.tmpCoords = c.tmpCoords.clone();
        this.kernelIntervalHelper = c.kernelIntervalHelper;
        this.kernelMask = c.kernelMask.clone();
    }

    HyperEllipsoidRegion copy() {
        return new HyperEllipsoidRegion(this);
    }

    private boolean[] createKernel(int... rs) {
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

    public boolean containsLocation(long... p) {
        double dist = 0;
        for (int d = 0; d < numDimensions(); d++) {
            double delta = p[d] - center[d];
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
        return kernelMask[maskIndex];
    }

    private boolean checkEllipsoidEquation(int... rs) {
        double dist = 0;
        for (int d = 0; d < numDimensions(); d++) {
            double delta = rs[d];
            dist += (delta * delta) / (radii[d] * radii[d]);
        }
        return dist <= 1;
    }

    Interval getBoundingBox() {
        return new FinalInterval(min, max);
    }

    void setLocationTo(HyperEllipsoidRegion c) {
        for (int d = 0; d < center.length; d++) {
            this.center[d] = c.center[d];
            this.min[d] = c.min[d];
            this.max[d] = c.max[d];
        }
    }

    void setLocationTo(Localizable c) {
        c.localize(this.center);
        updateMinMax();
    }

    public void setLocationTo(long... location) {
        for (int d = 0; d < center.length; d++) {
            this.center[d] = location[d];
        }
        updateMinMax();
    }

    long computeSize() {
        AtomicLong r = new AtomicLong(0);
        scan(
                0,
                (center, dist, axis) -> {
                    r.addAndGet(2 * dist + 1);
                    return 2 * dist + 1;
                },
                true
        );
        return r.get();
    }

    /**
     * Non recursive traversal of all the pixels from a hypersphere.
     * The idea is to pick an axis - d - and then for all values inside the sphere on that axis,
     * determine the intesection between the current hypersphere and the plane at x[d].
     * The intersection is a n-1 hypershpere for which we determine the limits
     * then recursively invoke the traversal for the n-1 hypersphere.
     * The recursion is stopped when the intersection is a 0-sphere or a segment between 2 points.
     *
     * @param startAxis        axis from which to start the hypersphere traversal
     * @param segmentProcessor
     * @param leftoToRight     - if true traverse the plane intersection axis from -radius to +radius, otherwise
     *                         traverse it from +radius to -radius
     */
    void scan(int startAxis, SegmentProcessor segmentProcessor, boolean leftoToRight) {
        int[] currentRadius = new int[numDimensions()];
        int[] radiusLimits = new int[numDimensions()];
        long[] currentPoint = new long[numDimensions()];
        int[] axesStack = new int[numDimensions()];
        int currentAxis = startAxis;
        System.arraycopy(radii, 0, radiusLimits, 0, numDimensions());
        currentRadius[currentAxis] = leftoToRight ? -radiusLimits[currentAxis] : radiusLimits[currentAxis];
        axesStack[0] = currentAxis;
        int axesStackIndex = 0;
        for (; ; ) {
            if (axesStackIndex + 1 < numDimensions()) {
                long r = currentRadius[currentAxis];
                long r0 = radiusLimits[currentAxis];
                if (leftoToRight && r <= r0 || !leftoToRight && r >= -r0) {
                    int newAxis = currentAxis + 1 < numDimensions() ? currentAxis + 1 : 0;
                    currentPoint[currentAxis] = r;
                    // r = ri * sqrt(1 - sum(xj^2/rj^2) for all j != i
                    double s = 0;
                    for (int d = 0; d < numDimensions(); d++) {
                        if (d == newAxis) {
                            s += 1;
                        } else {
                            s -= ((double) currentPoint[d] * currentPoint[d]) / ((double) radii[d] * radii[d]);
                        }
                    }
                    double newRadius = radii[newAxis] * Math.sqrt(s);
                    axesStack[++axesStackIndex] = newAxis;
                    radiusLimits[newAxis] = (int) newRadius;
                    currentRadius[newAxis] = (int) (leftoToRight ? -newRadius : newRadius);
                    currentAxis = newAxis;
                } else {
                    if (axesStackIndex == 0) {
                        // we are at the top level so we are done
                        return;
                    } else {
                        currentAxis = axesStack[--axesStackIndex];
                        currentPoint[currentAxis] = 0;
                        radiusLimits[currentAxis] = radii[currentAxis];
                        if (leftoToRight) {
                            ++currentRadius[currentAxis];
                        } else {
                            --currentRadius[currentAxis];
                        }
                    }
                }
            }
            if (axesStackIndex == axesStack.length - 1) {
                // the current intersection is just a line segment, so
                // we traverse the points on this segment between [-currentRadius, currentRadius]
                segmentProcessor.processSegment(currentPoint, radiusLimits[currentAxis], currentAxis);
                currentPoint[currentAxis] = 0;
                // pop the axis from the stack
                currentAxis = axesStack[--axesStackIndex];
                if (leftoToRight) {
                    ++currentRadius[currentAxis];
                } else {
                    --currentRadius[currentAxis];
                }
            }
        }
    }

    void updateMinMax() {
        CoordUtils.addCoords(center, radii, -1, min);
        CoordUtils.addCoords(center, radii, 1, max);
    }

    public int numDimensions() {
        return radii.length;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("center", center)
                .append("radii", radii)
                .toString();
    }
}
