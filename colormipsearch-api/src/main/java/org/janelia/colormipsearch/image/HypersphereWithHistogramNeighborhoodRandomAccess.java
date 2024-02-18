package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.function.Consumer;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public final class HypersphereWithHistogramNeighborhoodRandomAccess<T> extends AbstractEuclideanSpace implements RandomAccess<Neighborhood<T>> {
    private final RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess;
    private final RandomAccess<T> sourceAccess;
    private final PixelHistogram<T> histogram;
    private final HyperSphereNeighborhoodFactory<T> neighborhoodFactory;
    private final Neighborhood<T> currentNeighborhood;
    private final HyperSphereRegion currentHypersphereRegion;
    private final HyperSphereRegion previousHypersphereRegion;
    private final long[] workingPos;

    private interface SegmentProcessor {
        boolean processSegment(long[] centerCoords, long distance, int axis);
    }

    private static class HyperSphereRegion {
        final int numDimensions;
        final long radius;
        private final long sqRadius; // squareRadius
        // region center
        final long[] center;
        // region boundaries
        final long[] min;
        final long[] max;
        // current point
        final long[] currentPoint;
        // ignored axes
        final boolean[] scannedAxes;

        HyperSphereRegion(long radius, int numDimensions) {
            this.radius = radius;
            this.numDimensions = numDimensions;
            this.center = new long[numDimensions];
            this.min = new long[numDimensions];
            this.max = new long[numDimensions];
            this.sqRadius = radius * radius;
            this.currentPoint = new long[numDimensions];
            this.scannedAxes = new boolean[numDimensions];
        }

        HyperSphereRegion(HyperSphereRegion c) {
            this.radius = c.radius;
            this.numDimensions = c.numDimensions;
            this.sqRadius = c.sqRadius;
            this.center = c.center.clone();
            this.min = c.min.clone();
            this.max = c.max.clone();
            this.currentPoint = new long[numDimensions];
            this.scannedAxes = new boolean[numDimensions];
        }

        HyperSphereRegion copy() {
            return new HyperSphereRegion(this);
        }

        void copyFrom(HyperSphereRegion c) {
            for (int d = 0; d < center.length; d++) {
                this.center[d] = c.center[d];
                this.min[d] = c.min[d];
                this.max[d] = c.max[d];
            }
        }

        void updateMinMax() {
            CoordUtils.addCoord(center, -radius, min);
            CoordUtils.addCoord(center, radius, max);
        }

        boolean contains(long[] p) {
            long dist = 0;
            for (int d = 0; d < center.length; d++) {
                long dcoord = p[d] - center[d];
                dist += dcoord * dcoord;
            }
            return dist <= sqRadius;
        }

        private boolean traverseSphere(long currentRadius,
                                       int currentAxis,
                                       SegmentProcessor segmentProcessor) {
            if (currentRadius == 0) {
                return segmentProcessor.processSegment(currentPoint, 0, currentAxis);
            } else {
                int newAxis;
                for (int nextAxis = currentAxis+1;;nextAxis++) {
                    if (nextAxis == scannedAxes.length) {
                        // rollover
                        nextAxis = 0;
                    }
                    if (nextAxis == currentAxis) {
                        // reached the start position so no unscanned axis is left
                        newAxis = -1;
                        break;
                    }
                    if (!scannedAxes[nextAxis]) {
                        // unscanned axis found
                        newAxis = nextAxis;
                        break;
                    }
                }
                if (newAxis == -1) {
                    // the current intersection is just a line segment so
                    // we traverse the points on this segment between [-currentRadius, currentRadius]
                    return segmentProcessor.processSegment(currentPoint, currentRadius, currentAxis);
                } else {
                    scannedAxes[currentAxis] = true;
                    for (long r = currentRadius; r > 0; r--) {
                        // the hyper plane that intersect the sphere is x0 = r
                        // and the intersection is the n-1 hypershpere x1^2 + x2^2 + ... x(n-1)^2 = r^2
                        long newRadius = (long)Math.sqrt(currentRadius*currentRadius - r*r);
                        currentPoint[currentAxis] = r;

                        traverseSphere(newRadius, newAxis, segmentProcessor);
                        currentPoint[currentAxis] = -r;
                        traverseSphere(newRadius, newAxis, segmentProcessor);
                    }
                    currentPoint[currentAxis] = 0;
                    traverseSphere(currentRadius, newAxis, segmentProcessor);
                    scannedAxes[currentAxis] = false;
                    currentPoint[currentAxis] = 0;
                    return true;
                }
            }
        }


    }

    public HypersphereWithHistogramNeighborhoodRandomAccess(RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess,
                                                            RandomAccess<T> sourceAccess,
                                                            PixelHistogram<T> histogram,
                                                            long radius) {
        super(sourceAccess.numDimensions());
        this.sourceNeighborhoodAccess = sourceNeighborhoodAccess;
        this.sourceAccess = sourceAccess;
        this.histogram = histogram;
        this.currentHypersphereRegion = new HyperSphereRegion(radius, numDimensions());
        this.previousHypersphereRegion = new HyperSphereRegion(radius, numDimensions());
        this.neighborhoodFactory = HyperSphereNeighborhood.factory();
        this.currentNeighborhood = this.neighborhoodFactory.create(currentHypersphereRegion.center, currentHypersphereRegion.radius, sourceAccess);
        this.workingPos = createWorkingPos(radius);
    }

    private long[] createWorkingPos(long radius) {
        long[] pos = new long[numDimensions()];
        Arrays.fill(pos, -radius - 1);
        return pos;
    }

    private HypersphereWithHistogramNeighborhoodRandomAccess(final HypersphereWithHistogramNeighborhoodRandomAccess<T> c) {
        super(c.numDimensions());
        this.sourceNeighborhoodAccess = c.sourceNeighborhoodAccess.copy();
        this.sourceAccess = c.sourceAccess.copy();
        this.histogram = c.histogram.copy();
        this.currentHypersphereRegion = c.currentHypersphereRegion.copy();
        this.previousHypersphereRegion = c.previousHypersphereRegion.copy();
        this.neighborhoodFactory = c.neighborhoodFactory;
        this.currentNeighborhood = this.neighborhoodFactory.create(currentHypersphereRegion.center, currentHypersphereRegion.radius, sourceAccess);
        this.workingPos = c.workingPos.clone();
    }

    @Override
    public HypersphereWithHistogramNeighborhoodRandomAccess<T> copy() {
        return new HypersphereWithHistogramNeighborhoodRandomAccess<>(this);
    }

    @Override
    public long getLongPosition(int d) {
        return sourceNeighborhoodAccess.getLongPosition(d);
    }

    @Override
    public void fwd(int d) {
        updatedHyperspherPos(d, () -> ++currentHypersphereRegion.center[d]);
    }

    @Override
    public void bck(int d) {
        updatedHyperspherPos(d, () -> --currentHypersphereRegion.center[d] );
    }

    @Override
    public void move(int distance, int d) {
        updatedHyperspherPos(d, () -> currentHypersphereRegion.center[d] += distance);
    }

    @Override
    public void move(long distance, int d) {
        updatedHyperspherPos(d, () -> currentHypersphereRegion.center[d] += distance);
    }

    @Override
    public void move(Localizable distance) {
        updatedHyperspherPos(0, () -> CoordUtils.addCoords(currentHypersphereRegion.center, distance, currentHypersphereRegion.center));
    }

    @Override
    public void move(int[] distance) {
        updatedHyperspherPos(0, () -> CoordUtils.addCoords(currentHypersphereRegion.center, distance, currentHypersphereRegion.center));
    }

    @Override
    public void move(long[] distance) {
        updatedHyperspherPos(0, () -> CoordUtils.addCoords(currentHypersphereRegion.center, distance, currentHypersphereRegion.center));
    }

    @Override
    public void setPosition(Localizable position) {
        updatedHyperspherPos(0, () -> CoordUtils.setCoords(position, currentHypersphereRegion.center));
    }

    @Override
    public void setPosition(int[] position) {
        updatedHyperspherPos(0, () -> CoordUtils.setCoords(position, currentHypersphereRegion.center));
    }

    @Override
    public void setPosition(long[] position) {
        updatedHyperspherPos(0, () -> CoordUtils.setCoords(position, currentHypersphereRegion.center));
    }

    @Override
    public void setPosition(int position, int d) {
        updatedHyperspherPos(d, () -> currentHypersphereRegion.center[d] = position);
    }

    @Override
    public void setPosition(long position, int d) {
        updatedHyperspherPos(d, () -> currentHypersphereRegion.center[d] = position);
    }

    @Override
    public Neighborhood<T> get() {
        return currentNeighborhood;
    }

    public T getPixel() {
        return histogram.maxVal();
    }

    private void updatedHyperspherPos(int axis, Runnable action) {
        previousHypersphereRegion.copyFrom(currentHypersphereRegion);
        action.run();
        currentHypersphereRegion.updateMinMax();
        if (workingPos[0] < -currentHypersphereRegion.radius) {
            initializeHistogram(axis);
        } else {
            updateHistogram(axis);
        }
    }

    private void initializeHistogram(int axis) {
        currentHypersphereRegion.traverseSphere(
                currentHypersphereRegion.radius,
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentHypersphereRegion.center, centerCoords, workingPos);
                        sourceAccess.setPosition(workingPos);
                        histogram.add(sourceAccess.get());
                    }
                    return true;
                }
        );
    }

    private boolean removePointFromPrevNeighborhood(long[] point) {
        if (currentHypersphereRegion.contains(point)) {
            return false;
        }
        sourceAccess.setPosition(point);
        histogram.remove(sourceAccess.get());
        return true;
    }

    private boolean addPointFromNewNeighborhood(long[] point) {
        if (previousHypersphereRegion.contains(point)) {
            return false;
        }
        sourceAccess.setPosition(point);
        histogram.add(sourceAccess.get());
        return true;
    }

    private void updateHistogram(int axis) {
        previousHypersphereRegion.traverseSphere(
                previousHypersphereRegion.radius,
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (!removePointFromPrevNeighborhood(CoordUtils.addCoords(previousHypersphereRegion.center, centerCoords, workingPos))) {
                            return false;
                        }

                        centerCoords[d] = -r;
                        if (!removePointFromPrevNeighborhood(CoordUtils.addCoords(previousHypersphereRegion.center, centerCoords, workingPos))) {
                            return false;
                        }
                    }
                    centerCoords[d] = 0;
                    return removePointFromPrevNeighborhood(CoordUtils.addCoords(previousHypersphereRegion.center, centerCoords, workingPos));
                }
        );
        currentHypersphereRegion.traverseSphere(
                currentHypersphereRegion.radius,
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (!addPointFromNewNeighborhood(CoordUtils.addCoords(currentHypersphereRegion.center, centerCoords, workingPos))) {
                            return false;
                        }

                        centerCoords[d] = -r;
                        if (!addPointFromNewNeighborhood(CoordUtils.addCoords(currentHypersphereRegion.center, centerCoords, workingPos))) {
                            return false;
                        }
                    }
                    centerCoords[d] = 0;
                    return addPointFromNewNeighborhood(CoordUtils.addCoords(currentHypersphereRegion.center, centerCoords, workingPos));
                }
        );
    }
}
