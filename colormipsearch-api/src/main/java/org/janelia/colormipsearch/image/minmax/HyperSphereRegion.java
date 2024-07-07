package org.janelia.colormipsearch.image.minmax;

import java.util.concurrent.atomic.AtomicLong;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.image.CoordUtils;

public class HyperSphereRegion extends AbstractEuclideanSpace {

    public interface SegmentProcessor {
        /**
         * Process pixels on the segment from the given center position left and right along the axis
         * up to the specified distance.
         * @param centerCoords
         * @param distance
         * @param axis
         * @returnn number of pixes processed
         */
        long processSegment(long[] centerCoords, long distance, int axis);
    }

    final long[] radii;
    // region center
    final long[] center;
    // region boundaries
    final long[] min;
    final long[] max;

    HyperSphereRegion(long[] radii) {
        super(radii.length);
        this.radii = radii;
        this.center = new long[n];
        this.min = new long[n];
        this.max = new long[n];
    }

    private HyperSphereRegion(HyperSphereRegion c) {
        super(c.n);
        this.radii = c.radii;
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
    }

    HyperSphereRegion copy() {
        return new HyperSphereRegion(this);
    }

    boolean contains(long[] p) {
        double dist = 0;
        for (int d = 0; d < n; d++) {
            double delta = p[d] - center[d];
            dist += (delta * delta) / (radii[d] * radii[d]);
        }
        return dist <= 1;
    }

    Interval getBoundingBox() {
        return new FinalInterval(min, max);
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
     * @param startAxis axis from which to start the hypersphere traversal
     * @param segmentProcessor
     * @param leftoToRight - if true traverse the plane intersection axis from -radius to +radius, otherwise
     *                     traverse it from +radius to -radius
     */
    void scan(int startAxis, SegmentProcessor segmentProcessor, boolean leftoToRight) {
        long[] currentRadius = new long[n];
        long[] radiusLimits = new long[n];
        long[] currentPoint = new long[n];
        int[] axesStack = new int[n];
        int currentAxis = startAxis;
        radiusLimits[currentAxis] = radii[currentAxis];
        currentRadius[currentAxis] = leftoToRight ? -radiusLimits[currentAxis] : radiusLimits[currentAxis];
        axesStack[0] = currentAxis;
        int axesStackIndex = 0;
        for (; ; ) {
            if (axesStackIndex > 0 && currentRadius[currentAxis] == 0) {
                // if the stack index is 0 and the radius is 0 then the input radius must have been 0
                // right now I am not handling that case
                segmentProcessor.processSegment(currentPoint, 0, currentAxis);
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
                        currentPoint[currentAxis] = r;
                        // r = ri * sqrt(1 - sum(xj^2/rj^2) for all j != i
                        double s = 0;
                        for (int d = 0; d < n; d++) {
                            if (d == newAxis) {
                                s += 1;
                            } else {
                                double delta = currentRadius[d];
                                s -= (delta * delta) / ((double)radii[d] * radii[d]);
                            }
                        }
                        long newRadius = (long) (radii[newAxis] * Math.sqrt(s));
                        axesStack[++axesStackIndex] = newAxis;
                        radiusLimits[newAxis] = newRadius;
                        currentRadius[newAxis] = leftoToRight ? -newRadius : newRadius;
                        currentAxis = newAxis;
                    } else {
                        if (axesStackIndex == 0) {
                            // we are at the top level so we are done
                            return;
                        } else {
                            currentPoint[currentAxis] = 0;
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
                    segmentProcessor.processSegment(currentPoint, r >= 0 ? r : -r, currentAxis);
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
