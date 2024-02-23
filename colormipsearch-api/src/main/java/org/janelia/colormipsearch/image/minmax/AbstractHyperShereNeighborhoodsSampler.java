package org.janelia.colormipsearch.image.minmax;

import java.util.Arrays;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.PixelHistogram;

abstract class AbstractHyperShereNeighborhoodsSampler<T> extends AbstractEuclideanSpace implements Localizable, Positionable, Sampler<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final RandomAccess<T> sourceAccess;
    private final Interval sourceInterval;
    private final PixelHistogram<T> pixelHistogram;

    private final HyperSphereRegion currentNeighborhoodRegion;
    private final HyperSphereRegion prevNeighborhoodRegion;
    private final Neighborhood<T> currentNeighborhood;
    private boolean requireHistogramInit;

    AbstractHyperShereNeighborhoodsSampler(RandomAccessible<T> source,
                                           int radius,
                                           PixelHistogram<T> pixelHistogram,
                                           Interval accessInterval) {
        super(source.numDimensions());
        this.source = source;
        this.pixelHistogram = pixelHistogram;
        this.currentNeighborhoodRegion = new HyperSphereRegion(source.numDimensions(), radius);
        this.prevNeighborhoodRegion = new HyperSphereRegion(source.numDimensions(), radius);

        Interval sourceAccessInterval = accessInterval == null && source instanceof Interval ? (Interval) source : accessInterval;
        if (sourceAccessInterval == null) {
            sourceInterval = null;
        } else {
            sourceInterval = accessInterval;
        }
        this.sourceAccess = sourceInterval == null ? source.randomAccess() : source.randomAccess(sourceInterval);
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, sourceAccess);
        resetCurrentPos();
    }

    AbstractHyperShereNeighborhoodsSampler(AbstractHyperShereNeighborhoodsSampler<T> c) {
        super(c.n);
        this.source = c.source;
        this.pixelHistogram = c.pixelHistogram.copy();
        this.currentNeighborhoodRegion = c.currentNeighborhoodRegion.copy();
        this.prevNeighborhoodRegion = c.prevNeighborhoodRegion.copy();
        this.sourceInterval = c.sourceInterval;
        this.sourceAccess = c.sourceAccess.copy();
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, sourceAccess);
        this.requireHistogramInit = c.requireHistogramInit;
    }

    void resetCurrentPos() {
        if (sourceInterval == null) {
            Arrays.fill(currentNeighborhoodRegion.center, 0);
        } else {
            CoordUtils.setCoords(sourceInterval.minAsLongArray(), currentNeighborhoodRegion.center);
        }
        currentNeighborhoodRegion.updateMinMax();
        requireHistogramInit = true;
    }

    @Override
    public long getLongPosition(int d) {
        return currentNeighborhoodRegion.center[d];
    }

    @Override
    public Neighborhood<T> get() {
        return currentNeighborhood;
    }

    @Override
    public void fwd(int d) {
        updatedPos(d, () -> ++currentNeighborhoodRegion.center[d]);
    }

    @Override
    public void bck(int d) {
        updatedPos(d, () -> --currentNeighborhoodRegion.center[d] );
    }

    @Override
    public void move(int distance, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] += distance);

    }

    @Override
    public void move(long distance, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] += distance);
    }

    @Override
    public void move(Localizable distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(int[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(long[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(Localizable position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(int[] position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(long[] position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(int position, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] = position);
    }

    @Override
    public void setPosition(long position, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] = position);
    }

    private void updatedPos(int axis, Runnable updateAction) {
        prevNeighborhoodRegion.setLocationTo(currentNeighborhoodRegion);
        updateAction.run();
        currentNeighborhoodRegion.updateMinMax();
        if (requireHistogramInit) {
            initializeHistogram(axis);
            requireHistogramInit = false;
        } else {
            updateHistogram(axis);
        }
    }

    private void initializeHistogram(int axis) {
        pixelHistogram.clear();
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, int distance, int d) -> {
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos);
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                    }
                    return 2 * distance + 1;
                },
                true
        );
    }

    private void updateHistogram(int axis) {
        prevNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, int distance, int d) -> {
                    int n = 0;
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos))) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        pixelHistogram.remove(sourceAccess.setPositionAndGet(workingPos));
                        n++;

                        centerCoords[d] = -r;
                        if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos))) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        pixelHistogram.remove(sourceAccess.setPositionAndGet(workingPos));
                        n++;
                    }
                    centerCoords[d] = 0;
                    if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos))) {
                        // center is both in the current and prev neighborhood so it can still be considered
                        return n;
                    }
                    // center is only in prev neighborhood, so we shouldn't consider it anymore
                    pixelHistogram.remove(sourceAccess.setPositionAndGet(workingPos));
                    return n + 1;
                },
                true
        );
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, int distance, int d) -> {
                    int n = 0;
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos))) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                        n++;

                        centerCoords[d] = -r;
                        if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos))) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                        n++;
                    }
                    centerCoords[d] = 0;
                    if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos))) {
                        // center is both in the current and prev neighborhood so it was already considered
                        return n;
                    }
                    // center is only in current neighborhood
                    pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                    return n + 1;
                },
                false
        );
    }

}
