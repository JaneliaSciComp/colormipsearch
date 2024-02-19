package org.janelia.colormipsearch.image;

import java.util.Arrays;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.shape.HyperSphereRegion;

public final class HypersphereWithHistogramNeighborhoodRandomAccess<T> extends AbstractEuclideanSpace implements RandomAccess<Neighborhood<T>> {
    private final RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess;
    private final RandomAccess<T> sourceAccess;
    private final PixelHistogram<T> histogram;
    private final HyperSphereNeighborhoodFactory<T> neighborhoodFactory;
    private final Neighborhood<T> currentNeighborhood;
    private final HyperSphereRegion currentHypersphereRegion;
    private final HyperSphereRegion previousHypersphereRegion;
    private final long[] workingPos;

    public HypersphereWithHistogramNeighborhoodRandomAccess(RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess,
                                                            RandomAccess<T> sourceAccess,
                                                            PixelHistogram<T> histogram,
                                                            int radius) {
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

    private void updateHistogram(int axis) {
        previousHypersphereRegion.traverseSphere(
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

}
