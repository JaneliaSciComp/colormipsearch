package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.util.Intervals;

public class MaxFilterRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    private final HyperEllipsoidRegion currentNeighborhoodRegion;
    private final HyperEllipsoidRegion prevNeighborhoodRegion;
    private final PixelHistogram<T> slidingNeighborhoodHistogram;
    private final Interval dataInterval;
    private boolean requireHistogramInit;

    MaxFilterRandomAccess(RandomAccess<T> source,
                          int[] radii,
                          PixelHistogram<T> slidingNeighborhoodHistogram,
                          Interval dataInterval) {
        super(source, source.numDimensions());
        this.slidingNeighborhoodHistogram = slidingNeighborhoodHistogram;
        this.dataInterval = dataInterval;
        this.currentNeighborhoodRegion = new HyperEllipsoidRegion(radii);
        this.prevNeighborhoodRegion = new HyperEllipsoidRegion(radii);
        currentNeighborhoodRegion.setLocationTo(source);
        prevNeighborhoodRegion.setLocationTo(source);
        requireHistogramInit = true;
    }

    private MaxFilterRandomAccess(MaxFilterRandomAccess<T> c) {
        super(c);
        this.slidingNeighborhoodHistogram = c.slidingNeighborhoodHistogram.copy();
        this.dataInterval = c.dataInterval;
        this.currentNeighborhoodRegion = c.currentNeighborhoodRegion.copy();
        this.prevNeighborhoodRegion = c.prevNeighborhoodRegion.copy();
        this.requireHistogramInit = c.requireHistogramInit;
    }

    @Override
    public void fwd(final int d) {
        addAccessPos(1, d);
        updatedPos(d);
    }

    @Override
    public void bck(final int d) {
        addAccessPos(-1, d);
        updatedPos(d);
    }

    @Override
    public void move(final int distance, final int d) {
        addAccessPos(distance, d);
        updatedPos(d);
    }

    @Override
    public void move(final long distance, final int d) {
        addAccessPos(distance, d);
        updatedPos(d);
    }

    @Override
    public void move(final Localizable distance) {
        addAccessPos(distance);
        updatedPos(0);
    }

    @Override
    public void move(final int[] distance) {
        addAccessPos(distance);
        updatedPos(0);
    }

    @Override
    public void move(final long[] distance) {
        addAccessPos(distance);
        updatedPos(0);
    }

    @Override
    public void setPosition(final int[] location) {
        setAccessPos(location);
        updatedPos(0);
    }

    @Override
    public void setPosition(final long[] location) {
        setAccessPos(location);
        updatedPos(0);
    }

    @Override
    public void setPosition(final Localizable location) {
        setAccessPos(location);
        updatedPos(0);
    }

    @Override
    public void setPosition(final int location, final int d) {
        setAccessPos(location, d);
        updatedPos(d);
    }

    @Override
    public void setPosition(final long location, final int d) {
        setAccessPos(location, d);
        updatedPos(d);
    }

    @Override
    public T get() {
        return slidingNeighborhoodHistogram.maxVal();
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<T>(this);
    }

    private void updatedPos(int axis) {
        prevNeighborhoodRegion.setLocationTo(currentNeighborhoodRegion);
        currentNeighborhoodRegion.setLocationTo(this);
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
        slidingNeighborhoodHistogram.clear();
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, int distance, int d) -> {
                    int n = 0;
                    for (int r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
                        if (Intervals.contains(dataInterval, currentNeighborhoodRegion.tmpCoords)) {
                            T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                            slidingNeighborhoodHistogram.add(px);
                            n++;
                        }
                    }
                    return n;
                },
                HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
        );
    }

    private void updateHistogram(int axis) {
        currentNeighborhoodRegion.scan(
                axis,
                this::scanCurrentRegion,
                HyperEllipsoidRegion.ScanDirection.HIGH_TO_LOW
        );
        prevNeighborhoodRegion.scan(
                axis,
                this::scanPrevRegion,
                HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
        );
    }

    private long scanCurrentRegion(long[] centerCoords, int distance, int d) {
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (Intervals.contains(dataInterval, currentNeighborhoodRegion.tmpCoords)) {
                if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
                    // point is both in the current and prev neighborhood so it was already considered
                    return n;
                }
                // new point found only in current neighborhood
                T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                slidingNeighborhoodHistogram.add(px);
                n++;
            }

            centerCoords[d] = -r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (Intervals.contains(dataInterval, currentNeighborhoodRegion.tmpCoords)) {
                if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
                    // point is both in the current and prev neighborhood so it was already considered
                    return n;
                }
                // new point found only in current neighborhood
                T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                slidingNeighborhoodHistogram.add(px);
                n++;
            }
        }
        centerCoords[d] = 0;
        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
        if (Intervals.contains(dataInterval, currentNeighborhoodRegion.tmpCoords)) {
            if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
                // center is both in the current and prev neighborhood so it was already considered
                return n;
            }
            // center is only in current neighborhood
            T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.add(px);
            n++;
        }
        return n;
    }

    private long scanPrevRegion(long[] centerCoords, int distance, int d) {
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
            if (Intervals.contains(dataInterval, prevNeighborhoodRegion.tmpCoords)) {
                if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
                    // point is both in the current and prev neighborhood so it can still be considered
                    return n;
                }
                // point only in prev neighborhood, so we shouldn't consider it anymore
                T px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
                slidingNeighborhoodHistogram.remove(px);
                n++;
            }

            centerCoords[d] = -r;
            CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
            if (Intervals.contains(dataInterval, prevNeighborhoodRegion.tmpCoords)) {
                if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
                    // point is both in the current and prev neighborhood so it can still be considered
                    return n;
                }
                // point only in prev neighborhood, so we shouldn't consider it anymore
                T px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
                slidingNeighborhoodHistogram.remove(px);
                n++;
            }
        }
        centerCoords[d] = 0;
        CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
        if (Intervals.contains(dataInterval, prevNeighborhoodRegion.tmpCoords)) {
            if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
                // center is both in the current and prev neighborhood so it can still be considered
                return n;
            }
            // center is only in prev neighborhood, so we shouldn't consider it anymore
            T px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.remove(px);
            n++;
        }
        return n;
    }
}
