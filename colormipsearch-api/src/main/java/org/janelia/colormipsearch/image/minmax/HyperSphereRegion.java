package org.janelia.colormipsearch.image.minmax;

import java.util.concurrent.atomic.AtomicLong;

import net.imglib2.AbstractEuclideanSpace;
import org.janelia.colormipsearch.image.CoordUtils;

public class HyperSphereRegion extends AbstractEuclideanSpace {

    public interface SegmentProcessor {
        boolean processSegment(long[] centerCoords, long distance, int axis);
    }

    final long radius;
    // region center
    final long[] center;
    // region boundaries
    final long[] min;
    final long[] max;
    // working values
    private final long sqRadius; // squareRadius
    private final long[] currentRadius;
    private final long[] radiusLimits;
    private final long[] currentPoint;
    private final int[] axesStack;

    HyperSphereRegion(int n, long radius) {
        super(n);
        this.radius = radius;
        this.center = new long[n];
        this.min = new long[n];
        this.max = new long[n];
        this.sqRadius = radius * radius;
        this.currentRadius = new long[n];
        this.radiusLimits = new long[n];
        this.currentPoint = new long[n];
        this.axesStack = new int[n];
    }

    private HyperSphereRegion(HyperSphereRegion c) {
        super(c.n);
        this.radius = c.radius;
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
        this.sqRadius = radius * radius;
        this.currentRadius = new long[n];
        this.radiusLimits = new long[n];
        this.currentPoint = new long[n];
        this.axesStack = new int[n];
    }

    HyperSphereRegion copy() {
        return new HyperSphereRegion(this);
    }

    boolean contains(long[] p) {
        long dist = 0;
        for (int d = 0; d < n; d++) {
            long dcoord = p[d] - center[d];
            dist += dcoord * dcoord;
        }
        return dist <= sqRadius;
    }

    void setLocationTo(HyperSphereRegion c) {
        for (int d = 0; d < center.length; d++) {
            this.center[d] = c.center[d];
            this.min[d] = c.min[d];
            this.max[d] = c.max[d];
        }
    }

    long computeSize() {
        AtomicLong r = new AtomicLong(0);
        traverseSphere(
                0,
                (center, dist, axis) -> {
                    r.addAndGet(2 * dist + 1);
                    return true;
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
     * @param startAxis axis from which to start the hypersphere traversal
     * @param segmentProcessor
     * @param leftoToRight - if true traverse the plane intersection axis from -radius to +radius, otherwise
     *                     traverse it from +radius to -radius
     */
    void traverseSphere(int startAxis, SegmentProcessor segmentProcessor, boolean leftoToRight) {
        int currentAxis = startAxis;
        currentRadius[currentAxis] = leftoToRight ? -radius : radius;
        radiusLimits[currentAxis] = radius;
        axesStack[0] = currentAxis;
        int axesStackIndex = 0;
        for (; ; ) {
            if (axesStackIndex > 0 && currentRadius[currentAxis] == 0) {
                // if the stack index is 0 and the radius is 0 then the input radius must have been 0
                // right now I am not handling that case
                if (!segmentProcessor.processSegment(currentPoint, 0, currentAxis)) {
                    currentPoint[currentAxis] = 0;
                }
                currentAxis = axesStack[--axesStackIndex];
                if (leftoToRight) {
                    ++currentRadius[currentAxis];
                } else {
                    --currentRadius[currentAxis];
                }
            } else {
                long r = currentRadius[currentAxis];
                if (axesStackIndex + 1 < n) {
                    int newAxis = currentAxis + 1 < n ? currentAxis + 1 : 0;
                    long r0 = radiusLimits[currentAxis];
                    if (leftoToRight && r <= r0 || !leftoToRight && r >= -r0) {
                        long newRadius = (long) Math.sqrt(r0 * r0 - r * r);
                        currentPoint[currentAxis] = r;
                        axesStack[++axesStackIndex] = newAxis;
                        radiusLimits[newAxis] = newRadius;
                        currentRadius[newAxis] = leftoToRight ? -newRadius : newRadius;
                        currentAxis = newAxis;
                    } else {
                        currentPoint[currentAxis] = 0;
                        if (axesStackIndex == 0) {
                            // we are at the top level so we are done
                            return;
                        } else {
                            currentAxis = axesStack[--axesStackIndex];
                            if (leftoToRight) {
                                ++currentRadius[currentAxis];
                            } else {
                                --currentRadius[currentAxis];
                            }
                        }
                    }
                } else {
                    // the current intersection is just a line segment, so
                    // we traverse the points on this segment between [-currentRadius, currentRadius]
                    if (!segmentProcessor.processSegment(currentPoint, r >= 0 ? r : -r, currentAxis)) {
                        currentPoint[currentAxis] = 0;
                        if (axesStackIndex == 0) {
                            return;
                        }
                    }
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
    }

    void updateMinMax() {
        CoordUtils.addCoord(center, -radius, min);
        CoordUtils.addCoord(center, radius, max);
    }

}
