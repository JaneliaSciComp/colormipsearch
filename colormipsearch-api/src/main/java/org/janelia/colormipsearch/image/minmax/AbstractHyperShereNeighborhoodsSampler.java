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
import net.imglib2.util.Intervals;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.PixelHistogram;
import org.janelia.colormipsearch.image.RectIntervalHelper;

abstract class AbstractHyperShereNeighborhoodsSampler<T> extends AbstractEuclideanSpace implements Localizable, Positionable, Sampler<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final Interval sourceInterval;
    private final PixelHistogram<T> pixelHistogram;

    private final HyperSphereRegion currentNeighborhoodRegion;
    private final HyperSphereRegion prevNeighborhoodRegion;
    private final Neighborhood<T> currentNeighborhood;
    private boolean requireHistogramInit;

    AbstractHyperShereNeighborhoodsSampler(RandomAccessible<T> source,
                                           long[] radii,
                                           PixelHistogram<T> pixelHistogram,
                                           Interval accessInterval) {
        super(source.numDimensions());
        assert source.numDimensions() == radii.length;
        this.source = source;
        this.pixelHistogram = pixelHistogram;
        this.currentNeighborhoodRegion = new HyperSphereRegion(radii);
        this.prevNeighborhoodRegion = new HyperSphereRegion(radii);

        Interval sourceAccessInterval = accessInterval == null && source instanceof Interval ? (Interval) source : accessInterval;
        if (sourceAccessInterval == null) {
            sourceInterval = null;
        } else {
            sourceInterval = accessInterval;
        }
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, source);
        resetCurrentPos();
    }

    AbstractHyperShereNeighborhoodsSampler(AbstractHyperShereNeighborhoodsSampler<T> c) {
        super(c.n);
        this.source = c.source;
        this.pixelHistogram = c.pixelHistogram.copy();
        this.currentNeighborhoodRegion = c.currentNeighborhoodRegion.copy();
        this.prevNeighborhoodRegion = c.prevNeighborhoodRegion.copy();
        this.sourceInterval = c.sourceInterval;
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, source);
        this.requireHistogramInit = c.requireHistogramInit;
    }

    void resetCurrentPos() {
        if (sourceInterval == null) {
            Arrays.fill(currentNeighborhoodRegion.center, 0);
        } else {
            CoordUtils.setCoords(sourceInterval.minAsLongArray(), currentNeighborhoodRegion.center);
        }
        currentNeighborhoodRegion.updateMinMax();
        prevNeighborhoodRegion.setLocationTo(currentNeighborhoodRegion);
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
        updatedPos(d, () -> --currentNeighborhoodRegion.center[d]);
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
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, 1, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(int[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, 1, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(long[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, 1, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(Localizable position) {
        CoordUtils.setCoords(position, currentNeighborhoodRegion.center);
    }

    @Override
    public void setPosition(int[] position) {
        CoordUtils.setCoords(position, currentNeighborhoodRegion.center);
    }

    @Override
    public void setPosition(long[] position) {
        CoordUtils.setCoords(position, currentNeighborhoodRegion.center);
    }

    @Override
    public void setPosition(int position, int d) {
        currentNeighborhoodRegion.center[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        currentNeighborhoodRegion.center[d] = position;
    }

    private void updatedPos(int axis, Runnable updateAction) {
        prevNeighborhoodRegion.setLocationTo(currentNeighborhoodRegion);
        updateAction.run();
        currentNeighborhoodRegion.updateMinMax();
        boolean outside = false;
        for (int d = 0; d < currentNeighborhoodRegion.numDimensions(); d++) {
            if (currentNeighborhoodRegion.center[d] < 0) outside = true;
        }
        if (outside) return;
        if (requireHistogramInit ||
                CoordUtils.intersectIsVoid(
                        currentNeighborhoodRegion.min, currentNeighborhoodRegion.max,
                        prevNeighborhoodRegion.min, prevNeighborhoodRegion.max)) {
            initializeHistogram(axis);
            requireHistogramInit = false;
        } else {
            updateHistogram(axis);
        }
    }

    private void initializeHistogram(int axis) {
        pixelHistogram.clear();
        RandomAccess<T> sourceAccess = source.randomAccess();
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, workingPos);
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                    }
                    return 2 * distance + 1;
                },
                true
        );
    }

    private void updateHistogram(int axis) {
        RandomAccess<T> sourceAccess = source.randomAccess();
        prevNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    int n = 0;
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        pixelHistogram.remove(sourceAccess.setPositionAndGet(workingPos));
                        n++;

                        centerCoords[d] = -r;
                        if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        pixelHistogram.remove(sourceAccess.setPositionAndGet(workingPos));
                        n++;
                    }
                    centerCoords[d] = 0;
                    if (currentNeighborhoodRegion.contains(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
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
                (long[] centerCoords, long distance, int d) -> {
                    int n = 0;
                    long[] workingPos = new long[centerCoords.length];
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                        n++;

                        centerCoords[d] = -r;
                        if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        pixelHistogram.add(sourceAccess.setPositionAndGet(workingPos));
                        n++;
                    }
                    centerCoords[d] = 0;
                    if (prevNeighborhoodRegion.contains(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, workingPos))) {
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

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("currentNeighborhoodRegion", currentNeighborhoodRegion)
                .append("prevNeighborhoodRegion", prevNeighborhoodRegion)
                .append("currentNeighborhood", currentNeighborhood)
                .toString();
    }
}
