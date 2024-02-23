package org.janelia.colormipsearch.image.minmax;

import net.imglib2.AbstractEuclideanSpace;
import org.janelia.colormipsearch.image.CoordUtils;

public class HyperSphereRegion extends AbstractEuclideanSpace {

    public interface SegmentProcessor {
        void processSegment(long[] centerCoords, long distance, int axis);
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

    void traverseSphere(int startAxis, SegmentProcessor segmentProcessor) {
        traverseSphere1(startAxis, segmentProcessor);
    }

    void updateMinMax() {
        CoordUtils.addCoord(center, -radius, min);
        CoordUtils.addCoord(center, radius, max);
    }

    /**
     * Non recursive traversal
     *
     * @param startAxis
     * @param segmentProcessor
     */
    private void traverseSphere1(int startAxis, SegmentProcessor segmentProcessor) {
        int currentAxis = startAxis;
        currentRadius[currentAxis] = radius;
        radiusLimits[currentAxis] = radius;
        axesStack[0] = currentAxis;
        int axesStackIndex = 0;
        for (; ; ) {
            if (axesStackIndex > 0 && currentRadius[currentAxis] == 0) {
                // if the stack index is 0 and the radius is 0 then the input radius must have been 0
                // right now I am not handling that case
                segmentProcessor.processSegment(currentPoint, 0, currentAxis);
                currentAxis = axesStack[--axesStackIndex];
                --currentRadius[currentAxis];
            } else {
                long r = currentRadius[currentAxis];
                if (axesStackIndex + 1 < n) {
                    int newAxis = currentAxis + 1 < n ? currentAxis + 1 : 0;
                    long r0 = radiusLimits[currentAxis];
                    if (r >= -r0) {
                        long newRadius = (long) Math.sqrt(r0 * r0 - r * r);
                        currentPoint[currentAxis] = r;
                        axesStack[++axesStackIndex] = newAxis;
                        currentRadius[newAxis] = radiusLimits[newAxis] = newRadius;
                        currentAxis = newAxis;
                    } else {
                        currentPoint[currentAxis] = 0;
                        if (axesStackIndex == 0) {
                            // we are at the top level so we are done
                            return;
                        } else {
                            currentAxis = axesStack[--axesStackIndex];
                            --currentRadius[currentAxis];
                        }
                    }
                } else {
                    // the current intersection is just a line segment, so
                    // we traverse the points on this segment between [-currentRadius, currentRadius]
                    segmentProcessor.processSegment(currentPoint, r, currentAxis);
                    // pop the axis from the stack
                    currentAxis = axesStack[--axesStackIndex];
                    --currentRadius[currentAxis];
                }
            }
        }
    }

    /**
     * Recursive traversal.
     *
     * @param axisStackIndex
     * @param currentRadius
     * @param currentAxis
     * @param segmentProcessor
     */
    private void traverseSphere2(int axisStackIndex,
                                 long currentRadius,
                                 int currentAxis,
                                 SegmentProcessor segmentProcessor) {
        if (currentRadius == 0) {
            segmentProcessor.processSegment(currentPoint, 0, currentAxis);
        } else {
            if (axisStackIndex + 1 < n) {
                int newAxis = currentAxis + 1 < n ? currentAxis + 1 : 0;
                for (long r = currentRadius; r >= -currentRadius; r--) {
                    // the hyper plane that intersect the sphere is x0 = r
                    // and the intersection is the n-1 hypershpere x1^2 + x2^2 + ... x(n-1)^2 = r^2
                    long newRadius = (long) Math.sqrt(currentRadius * currentRadius - r * r);
                    currentPoint[currentAxis] = r;
                    traverseSphere2(axisStackIndex + 1, newRadius, newAxis, segmentProcessor);
                }
                currentPoint[currentAxis] = 0;
            } else {
                // the current intersection is just a line segment, so
                // we traverse the points on this segment between [-currentRadius, currentRadius]
                segmentProcessor.processSegment(currentPoint, currentRadius, currentAxis);
            }
        }
    }


}
