package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.util.Intervals;

public class MaxFilterRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    private final HyperEllipsoidRegion currentNeighborhoodRegion;
    private final HyperEllipsoidRegion prevNeighborhoodRegion;
    private final PixelHistogram<T> slidingNeighborhoodHistogram;
    private final Interval sourceDataInterval;
    private final Interval safeSourceDataInterval;
    private boolean requireHistogramInit;

    MaxFilterRandomAccess(RandomAccess<T> source,
                          Interval sourceDataInterval,
                          int[] radii,
                          PixelHistogram<T> slidingNeighborhoodHistogram) {
        super(source, source.numDimensions());
        this.slidingNeighborhoodHistogram = slidingNeighborhoodHistogram;
        this.sourceDataInterval = sourceDataInterval;
        this.safeSourceDataInterval = getShrinkedDataInterval(sourceDataInterval, radii);
        this.currentNeighborhoodRegion = new HyperEllipsoidRegion(radii);
        this.prevNeighborhoodRegion = new HyperEllipsoidRegion(radii);
        currentNeighborhoodRegion.setLocationTo(source);
        prevNeighborhoodRegion.setLocationTo(source);
        requireHistogramInit = true;
    }

    private MaxFilterRandomAccess(MaxFilterRandomAccess<T> c) {
        super(c);
        this.slidingNeighborhoodHistogram = c.slidingNeighborhoodHistogram.copy();
        this.sourceDataInterval = c.sourceDataInterval;
        this.safeSourceDataInterval = c.safeSourceDataInterval;
        this.currentNeighborhoodRegion = c.currentNeighborhoodRegion.copy();
        this.prevNeighborhoodRegion = c.prevNeighborhoodRegion.copy();
        this.requireHistogramInit = c.requireHistogramInit;
    }

    private Interval getShrinkedDataInterval(Interval interval, int[] radii) {
        long[] negativeRadii = new long[radii.length];
        for (int d = 0; d < radii.length; d++) {
            negativeRadii[d] = -radii[d];
        }
        return Intervals.expand(interval, negativeRadii);
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
        if (CoordUtils.contains(safeSourceDataInterval, currentNeighborhoodRegion.center)) {
            currentNeighborhoodRegion.scan(
                    axis,
                    this::unsafeInitialScanCurrentRegion,
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );
        } else {
            currentNeighborhoodRegion.scan(
                    axis,
                    this::initialScanCurrentRegion,
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );
        }
    }

    private void updateHistogram(int axis) {
        if (CoordUtils.contains(safeSourceDataInterval, currentNeighborhoodRegion.center)) {
            currentNeighborhoodRegion.scan(
                    axis,
                    this::unsafeScanCurrentRegion,
                    HyperEllipsoidRegion.ScanDirection.HIGH_TO_LOW
            );
        } else {
            currentNeighborhoodRegion.scan(
                    axis,
                    this::scanCurrentRegion,
                    HyperEllipsoidRegion.ScanDirection.HIGH_TO_LOW
            );
        }
        if (CoordUtils.contains(safeSourceDataInterval, prevNeighborhoodRegion.center)) {
            prevNeighborhoodRegion.scan(
                    axis,
                    this::unsafeScanPrevRegion,
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );
        } else {
            prevNeighborhoodRegion.scan(
                    axis,
                    this::scanPrevRegion,
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );
        }
    }

    private long initialScanCurrentRegion(long[] centerCoords, int distance, int d) {
        int n = 0;
        for (int r = distance; r >= -distance; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (CoordUtils.contains(sourceDataInterval, currentNeighborhoodRegion.tmpCoords)) {
                T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                slidingNeighborhoodHistogram.add(px);
                n++;
            }
        }
        return n;
    }

    private long unsafeInitialScanCurrentRegion(long[] centerCoords, int distance, int d) {
        for (int r = distance; r >= -distance; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            T px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.add(px);
        }
        return 2L * distance + 1;
    }

    private long scanCurrentRegion(long[] centerCoords, int distance, int d) {
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (CoordUtils.contains(sourceDataInterval, currentNeighborhoodRegion.tmpCoords)) {
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
            if (CoordUtils.contains(sourceDataInterval, currentNeighborhoodRegion.tmpCoords)) {
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
        if (CoordUtils.contains(sourceDataInterval, currentNeighborhoodRegion.tmpCoords)) {
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

    private long unsafeScanCurrentRegion(long[] centerCoords, int distance, int d) {
        T px;
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
                // point is both in the current and prev neighborhood so it was already considered
                return n;
            }
            // new point found only in current neighborhood
            px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.add(px);
            n++;

            centerCoords[d] = -r;
            CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
            if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
                // point is both in the current and prev neighborhood so it was already considered
                return n;
            }
            // new point found only in current neighborhood
            px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.add(px);
            n++;
        }
        centerCoords[d] = 0;
        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, currentNeighborhoodRegion.tmpCoords);
        if (prevNeighborhoodRegion.containsLocation(currentNeighborhoodRegion.tmpCoords)) {
            // center is both in the current and prev neighborhood so it was already considered
            return n;
        }
        // center is only in current neighborhood
        px = source.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
        slidingNeighborhoodHistogram.add(px);
        n++;
        return n;
    }

    private long scanPrevRegion(long[] centerCoords, int distance, int d) {
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
            if (CoordUtils.contains(sourceDataInterval, prevNeighborhoodRegion.tmpCoords)) {
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
            if (CoordUtils.contains(sourceDataInterval, prevNeighborhoodRegion.tmpCoords)) {
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
        if (CoordUtils.contains(sourceDataInterval, prevNeighborhoodRegion.tmpCoords)) {
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

    private long unsafeScanPrevRegion(long[] centerCoords, int distance, int d) {
        T px;
        int n = 0;
        for (int r = distance; r > 0; r--) {
            centerCoords[d] = r;
            CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
            if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
                // point is both in the current and prev neighborhood so it can still be considered
                return n;
            }
            // point only in prev neighborhood, so we shouldn't consider it anymore
            px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.remove(px);
            n++;

            centerCoords[d] = -r;
            CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
            if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
                // point is both in the current and prev neighborhood so it can still be considered
                return n;
            }
            // point only in prev neighborhood, so we shouldn't consider it anymore
            px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
            slidingNeighborhoodHistogram.remove(px);
            n++;
        }
        centerCoords[d] = 0;
        CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, prevNeighborhoodRegion.tmpCoords);
        if (currentNeighborhoodRegion.containsLocation(prevNeighborhoodRegion.tmpCoords)) {
            // center is both in the current and prev neighborhood so it can still be considered
            return n;
        }
        // center is only in prev neighborhood, so we shouldn't consider it anymore
        px = source.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
        slidingNeighborhoodHistogram.remove(px);
        n++;
        return n;
    }

}
