package org.janelia.colormipsearch.image.view;

import java.util.function.IntUnaryOperator;

import org.janelia.colormipsearch.image.Dimensions;

/**
 * A hyper-ellipsoidal region that can be positioned at different centers and supports
 * scanning its interior in a configurable direction. Used for incremental neighborhood
 * updates in sliding-window filters: by scanning from outside inward, we can detect
 * when we enter the intersection with another region and stop early.
 */
class HyperEllipsoidRegion {

    private static final int N_DIMENSIONS = 3;

    /**
     * Processes a segment of pixels along one axis of the ellipsoid.
     */
    interface SegmentProcessor {
        /**
         * Process pixels on the segment centered at the given coordinates,
         * extending left and right along the axis up to the specified distance.
         *
         * @param centerCoords relative coordinates of the segment center (from the region center)
         * @param distance     half-length of the segment along the axis
         * @param axis         which axis the segment runs along
         */
        void processSegment(int[] centerCoords, int distance, int axis);
    }

    enum ScanDirection {
        LOW_TO_HIGH(p -> -p, p -> p + 1),
        HIGH_TO_LOW(p -> p, p -> p - 1);

        final IntUnaryOperator initPos;
        final IntUnaryOperator nextPos;

        ScanDirection(IntUnaryOperator initPos, IntUnaryOperator nextPos) {
            this.initPos = initPos;
            this.nextPos = nextPos;
        }
    }

    final int rx;
    final int ry;
    final int rz;
    final int[] tmpCoords;
    // center position
    int cx;
    int cy;
    int cz;
    // min position
    int minx;
    int miny;
    int minz;
    // max position
    int maxx;
    int maxy;
    int maxz;

    HyperEllipsoidRegion(int rx, int ry, int rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.tmpCoords = new int[N_DIMENSIONS];
    }

    /**
     * Check if an absolute position falls inside this ellipsoid.
     */
    boolean containsLocation(int px, int py, int pz) {
        double dist = 0;
        for (int d = 0; d < N_DIMENSIONS; d++) {
            int radius = Dimensions.selectDim(rx, ry, rz, d);
            int center = Dimensions.selectDim(cx, cy, cz, d);
            int coord = Dimensions.selectDim(px, py, pz, d);
            if (radius == 0) {
                if (coord != center) {
                    // if a dimension is not used the coordinate in that dimension must be corresponding center on that axis
                    return false;
                }
            } else {
                double delta = coord - center;
                dist += (delta * delta) / ((double) radius * radius);
            }
        }
        return dist <= 1;
    }

    boolean containsLocation(int[] p) {
        double dist = 0;
        for (int d = 0; d < N_DIMENSIONS; d++) {
            int radius = Dimensions.selectDim(rx, ry, rz, d);
            int center = Dimensions.selectDim(cx, cy, cz, d);
            int coord = Dimensions.selectDim(p, d);
            if (radius == 0) {
                if (coord != center) {
                    // if a dimension is not used the coordinate in that dimension must be corresponding center on that axis
                    return false;
                }
            } else {
                double delta = coord - center;
                dist += (delta * delta) / ((double) radius * radius);
            }
        }
        return dist <= 1;
    }

    /**
     * Copy the location from another region.
     */
    void setLocationTo(HyperEllipsoidRegion c) {
        this.cx = c.cx;
        this.cy = c.cy;
        this.cz = c.cz;

        this.minx = c.minx;
        this.miny = c.miny;
        this.minz = c.minz;

        this.maxx = c.maxx;
        this.maxy = c.maxy;
        this.maxz = c.maxz;
    }

    boolean hasSameLocationAs(HyperEllipsoidRegion c) {
        return this.cx == c.cx && this.cy == c.cy && this.cz == c.cz;
    }

    /**
     * Set the center to the given coordinates and update min/max.
     */
    void setCenter(int cx, int cy, int cz) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        updateMinMax();
    }

    int[] center() {
        return new int[] {
            cx, cy, cz
        };
    }

    int[] min() {
        return new int[] {
            minx, miny, minz
        };
    }

    int[] max() {
        return new int[] {
            maxx, maxy, maxz
        };
    }

    /**
     * Non-recursive traversal of the hyper-ellipsoid interior.
     * <p>
     * Iterates dimension by dimension: for each position along the outer dimensions,
     * computes the radius of the intersection ellipsoid in the remaining dimensions.
     * At the innermost dimension, invokes the segment processor with the 1D segment.
     * <p>
     * The scan direction controls whether we start from the low or high end of each
     * axis range. This is critical for incremental updates: scanning from outside inward
     * allows early termination when we enter the intersection with another region.
     *
     * @param startAxis        axis from which to start the traversal
     * @param segmentProcessor processor for each 1D segment
     * @param direction        scan direction (LOW_TO_HIGH or HIGH_TO_LOW)
     */
    void scan(int startAxis, SegmentProcessor segmentProcessor, ScanDirection direction) {
        int[] currentRadius = new int[N_DIMENSIONS];
        int[] radiusLimits = new int[N_DIMENSIONS];
        int[] currentPoint = new int[N_DIMENSIONS];
        int[] axesStack = new int[N_DIMENSIONS];
        int currentAxis = startAxis;
        int axesStackIndex = 0;
        radiusLimits[currentAxis] = Dimensions.selectDim(rx, ry, rz, currentAxis);
        currentRadius[currentAxis] = direction.initPos.applyAsInt(radiusLimits[currentAxis]);
        axesStack[0] = currentAxis;
        for (; ;) {
            if (axesStackIndex + 1 < N_DIMENSIONS) {
                int r = currentRadius[currentAxis];
                int r0 = radiusLimits[currentAxis];
                if (Math.abs(r) <= r0) {
                    int newAxis = currentAxis + 1 < N_DIMENSIONS ? currentAxis + 1 : 0;
                    currentPoint[currentAxis] = r;
                    // r_new = r_i * sqrt(1 - sum(x_j^2 / r_j^2) for all j != newAxis)
                    double s = 1;
                    for (int d = 0; d < N_DIMENSIONS; d++) {
                        int cr = Dimensions.selectDim(rx, ry, rz, d);
                        if (d != newAxis && cr > 0) {
                            s -= ((double) currentPoint[d] * currentPoint[d]) / ((double) cr * cr);
                        }
                    }
                    int newRadius = (int) (Dimensions.selectDim(rx, ry, rz, newAxis) * Math.sqrt(Math.max(0, s)));
                    axesStack[++axesStackIndex] = newAxis;
                    radiusLimits[newAxis] = newRadius;
                    currentRadius[newAxis] = direction.initPos.applyAsInt(radiusLimits[newAxis]);
                    currentAxis = newAxis;
                } else {
                    if (axesStackIndex == 0) {
                        return;
                    } else {
                        currentAxis = axesStack[--axesStackIndex];
                        currentPoint[currentAxis] = 0;
                        radiusLimits[currentAxis] = Dimensions.selectDim(rx, ry, rz, currentAxis);
                        currentRadius[currentAxis] = direction.nextPos.applyAsInt(currentRadius[currentAxis]);
                    }
                }
            }
            if (axesStackIndex == axesStack.length - 1) {
                segmentProcessor.processSegment(currentPoint, radiusLimits[currentAxis], currentAxis);
                currentPoint[currentAxis] = 0;
                currentAxis = axesStack[--axesStackIndex];
                currentRadius[currentAxis] = direction.nextPos.applyAsInt(currentRadius[currentAxis]);
            }
        }
    }

    private void updateMinMax() {
        minx = cx - rx;
        miny = cy - ry;
        minz = cz - rz;

        maxx = cx + rx;
        maxy = cy + ry;
        maxz = cz + rz;
    }
}
