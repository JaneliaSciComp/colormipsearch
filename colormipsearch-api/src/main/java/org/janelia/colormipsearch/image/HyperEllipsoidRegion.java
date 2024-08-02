package org.janelia.colormipsearch.image;

import java.util.function.Function;

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
         */
        void processSegment(long[] centerCoords, int distance, int axis);
    }

    public enum ScanDirection {
        LOW_TO_HIGH(p -> -p, p -> p + 1),
        HIGH_TO_LOW(p -> p, p -> p - 1);

        private final Function<Integer, Integer> initPos;
        private final Function<Integer, Integer> nextPos;

        ScanDirection(Function<Integer, Integer> initPos, Function<Integer, Integer> nextPos) {
            this.initPos = initPos;
            this.nextPos = nextPos;
        }
    }

    final int numDimensions;
    final int[] radii;
    // region center
    final long[] center;
    // region boundaries
    final long[] min;
    final long[] max;
    final long[] tmpCoords;

    public HyperEllipsoidRegion(int... radii) {
        this.numDimensions = radii.length;
        this.radii = radii;
        this.center = new long[numDimensions];
        this.min = new long[numDimensions];
        this.max = new long[numDimensions];
        this.tmpCoords = new long[numDimensions];
    }

    HyperEllipsoidRegion(HyperEllipsoidRegion c) {
        this.numDimensions = c.numDimensions;
        this.radii = c.radii;
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
        this.tmpCoords = c.tmpCoords.clone();
    }

    HyperEllipsoidRegion copy() {
        return new HyperEllipsoidRegion(this);
    }

    public boolean containsLocation(long[] p) {
        double dist = 0;
        for (int d = 0; d < p.length; d++) {
            int delta = (int) (p[d] - center[d]);
            dist += ((double) delta * delta) / (radii[d] * radii[d]);
        }
        return dist <= 1;
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

    /**
     * Non recursive traversal of all the pixels from a hypersphere.
     * The idea is to pick an axis - d - and then for all values inside the sphere on that axis,
     * determine the intesection between the current hypersphere and the plane at x[d].
     * The intersection is a n-1 hypershpere for which we determine the limits
     * then recursively invoke the traversal for the n-1 hypersphere.
     * The recursion is stopped when the intersection is a 0-sphere or a segment between 2 points.
     *
     * @param startAxis        axis from which to start the hypersphere traversal
     * @param segmentProcessor processor for pixels along a "segment"
     * @param direction - scan direction
     */
    void scan(int startAxis, SegmentProcessor segmentProcessor, ScanDirection direction) {
        int[] currentRadius = new int[numDimensions];
        int[] radiusLimits = new int[numDimensions];
        long[] currentPoint = new long[numDimensions];
        int[] axesStack = new int[numDimensions];
        int currentAxis = startAxis;
        int axesStackIndex = 0;
        radiusLimits[currentAxis] = radii[currentAxis];
        currentRadius[currentAxis] = direction.initPos.apply(radiusLimits[currentAxis]);
        axesStack[0] = currentAxis;
        for ( ; ; ) {
            if (axesStackIndex + 1 < numDimensions) {
                int r = currentRadius[currentAxis];
                int r0 = radiusLimits[currentAxis];
                if (Math.abs(r) <= r0) {
                    int newAxis = currentAxis + 1 < numDimensions ? currentAxis + 1 : 0;
                    currentPoint[currentAxis] = r;
                    // r = ri * sqrt(1 - sum(xj^2/rj^2) for all j != i
                    double s = 1;
                    for (int d = 0; d < numDimensions; d++) {
                        if (d != newAxis) {
                            s -= ((double) currentPoint[d] * currentPoint[d]) / (radii[d] * radii[d]);
                        }
                    }
                    int newRadius = (int) (radii[newAxis] * Math.sqrt(s));
                    axesStack[++axesStackIndex] = newAxis;
                    radiusLimits[newAxis] = newRadius;
                    currentRadius[newAxis] = direction.initPos.apply(radiusLimits[newAxis]);
                    currentAxis = newAxis;
                } else {
                    if (axesStackIndex == 0) {
                        // we are at the top level so we are done
                        return;
                    } else {
                        currentAxis = axesStack[--axesStackIndex];
                        currentPoint[currentAxis] = 0;
                        radiusLimits[currentAxis] = radii[currentAxis];
                        currentRadius[currentAxis] = direction.nextPos.apply(currentRadius[currentAxis]);
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
                currentRadius[currentAxis] = direction.nextPos.apply(currentRadius[currentAxis]);
            }
        }
    }

    void updateMinMax() {
        CoordUtils.addCoords(center, radii, -1, min);
        CoordUtils.addCoords(center, radii, 1, max);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("center", center)
                .append("radii", radii)
                .toString();
    }
}
