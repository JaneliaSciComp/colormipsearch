package org.janelia.colormipsearch.image.shape;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import org.janelia.colormipsearch.image.CoordUtils;

public class HyperSphereRegion implements Shape {

    public interface SegmentProcessor {
        boolean processSegment(long[] centerCoords, long distance, int axis);
    }

    public final int numDimensions;
    public final long radius;
    private final long sqRadius; // squareRadius
    // region center
    public final long[] center;
    // region boundaries
    public final long[] min;
    public final long[] max;
    // current working values
    final long[] currentRadius;
    final long[] radiusLimits;
    final long[] currentPoint;
    final int[] axesStack;

    public HyperSphereRegion(long radius, int numDimensions) {
        this.radius = radius;
        this.numDimensions = numDimensions;
        this.center = new long[numDimensions];
        this.min = new long[numDimensions];
        this.max = new long[numDimensions];
        this.sqRadius = radius * radius;
        this.currentRadius = new long[numDimensions];
        this.radiusLimits = new long[numDimensions];
        this.currentPoint = new long[numDimensions];
        this.axesStack = new int[numDimensions];
    }

    HyperSphereRegion(HyperSphereRegion c) {
        this.radius = c.radius;
        this.numDimensions = c.numDimensions;
        this.sqRadius = c.sqRadius;
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
        this.currentRadius = new long[numDimensions];
        this.radiusLimits = new long[numDimensions];
        this.currentPoint = new long[numDimensions];
        this.axesStack = new int[numDimensions];
    }

    @Override
    public <T> IterableInterval<Neighborhood<T>> neighborhoods(RandomAccessibleInterval<T> source) {
        // !!!!!!!!
        return null;
    }

    @Override
    public <T> RandomAccessible<Neighborhood<T>> neighborhoodsRandomAccessible(RandomAccessible<T> source) {
        // !!!!!!!!
        return null;
    }

    @Override
    public <T> IterableInterval<Neighborhood<T>> neighborhoodsSafe(RandomAccessibleInterval<T> source) {
        // !!!!!!!!
        return null;
    }

    @Override
    public <T> RandomAccessible<Neighborhood<T>> neighborhoodsRandomAccessibleSafe(RandomAccessible<T> source) {
        // !!!!!!!!
        return null;
    }

    public HyperSphereRegion copy() {
        return new HyperSphereRegion(this);
    }

    public void copyFrom(HyperSphereRegion c) {
        for (int d = 0; d < center.length; d++) {
            this.center[d] = c.center[d];
            this.min[d] = c.min[d];
            this.max[d] = c.max[d];
        }
    }

    public void updateMinMax() {
        CoordUtils.addCoord(center, -radius, min);
        CoordUtils.addCoord(center, radius, max);
    }

    public boolean contains(long[] p) {
        long dist = 0;
        for (int d = 0; d < center.length; d++) {
            long dcoord = p[d] - center[d];
            dist += dcoord * dcoord;
        }
        return dist <= sqRadius;
    }

    public void traverseSphere(int startAxis, SegmentProcessor segmentProcessor) {
        traverseSphere1(startAxis, segmentProcessor);
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
        currentPoint[currentAxis] = 0;
        int axesStackIndex = 0;
        for (;;) {
            if (axesStackIndex > 0 && currentRadius[currentAxis] == 0) {
                // if the stack index is 0 and the radius is 0 then the input radius must have been 0
                // right now I am not handling that case
                segmentProcessor.processSegment(currentPoint, 0, currentAxis);
                currentAxis = axesStack[--axesStackIndex];
                --currentRadius[currentAxis];
            } else {
                long r = currentRadius[currentAxis];
                if (axesStackIndex + 1 < numDimensions) {
                    int newAxis = currentAxis + 1 < numDimensions ? currentAxis + 1 : 0;
                    long r0 = radiusLimits[currentAxis];
                    if (r >= -r0) {
                        long newRadius = (long) Math.sqrt(r0 * r0 - r * r);
                        currentPoint[currentAxis] = r;
                        axesStack[++axesStackIndex] = newAxis;
                        radiusLimits[newAxis] = newRadius;
                        currentRadius[newAxis] = newRadius;
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
            if (axisStackIndex + 1 < numDimensions) {
                int newAxis = currentAxis + 1 < numDimensions ? currentAxis + 1 : 0;
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
