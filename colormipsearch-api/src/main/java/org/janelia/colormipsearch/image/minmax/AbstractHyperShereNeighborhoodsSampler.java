package org.janelia.colormipsearch.image.minmax;

import java.util.Arrays;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.PixelHistogram;
import org.janelia.colormipsearch.image.RectIntervalHelper;

abstract class AbstractHyperShereNeighborhoodsSampler<T> extends AbstractEuclideanSpace implements Localizable, Positionable, Sampler<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final Interval sourceInterval;
    private final PixelHistogram<T> pixelHistogram;

    private final HyperSphereMask hypershpereMask;
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
        this.hypershpereMask = new HyperSphereMask(radii);
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
        this.hypershpereMask = c.hypershpereMask.copy();
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
        if (requireHistogramInit ||
                sourceInterval != null && sourceInterval.min(0) == currentNeighborhoodRegion.center[0] ||
                CoordUtils.intersectIsVoid(
                        currentNeighborhoodRegion.min, currentNeighborhoodRegion.max,
                        prevNeighborhoodRegion.min, prevNeighborhoodRegion.max)) {
            initializeHistogram(axis);
            requireHistogramInit = false;
        } else {
            updateHistogram(axis);
        }
    }

    private void initializeHistogramNew(int axis) {
        RandomAccessibleInterval<T> currNeighborhoodInterval = Views.interval(source, currentNeighborhoodRegion.getBoundingBox());
        long[] currStartMask = new long[source.numDimensions()];
        long[] currEndMask = new long[source.numDimensions()];
        for (int i = 0; i < source.numDimensions(); i++) {
            currStartMask[i] = 1;
            currEndMask[i] = 2 * hypershpereMask.radii[i] + 1;
        }
        RandomAccessibleInterval<UnsignedByteType> currInterval = hypershpereMask.getMaskInterval(
                new FinalInterval(currStartMask, currEndMask)
        );
        Cursor<UnsignedByteType> currMaskCursor = Views.flatIterable(currInterval).cursor();
        Cursor<T> curNeighbCursor = Views.flatIterable(currNeighborhoodInterval).cursor();
        while (curNeighbCursor.hasNext()) {
            int currMaskPx = currMaskCursor.next().get();
            curNeighbCursor.next();
            if (currMaskPx != 0) {
                pixelHistogram.add(curNeighbCursor.get());
            }
        }
    }

    private void updateHistogramNew(int axis) {
        RandomAccessibleInterval<T> prevNeighborhoodInterval = Views.interval(source, prevNeighborhoodRegion.getBoundingBox());
        RandomAccessibleInterval<T> currNeighborhoodInterval = Views.interval(source, currentNeighborhoodRegion.getBoundingBox());
        long[] prevStartMask = new long[source.numDimensions()];
        long[] prevEndMask = new long[source.numDimensions()];
        long[] currStartMask = new long[source.numDimensions()];
        long[] currEndMask = new long[source.numDimensions()];
        for (int i = 0; i < source.numDimensions(); i++) {
            prevStartMask[i] = 1;
            prevEndMask[i] = 2 * hypershpereMask.radii[i] + 1;
            currStartMask[i] = 0;
            currEndMask[i] = 2 * hypershpereMask.radii[i];
        }
        RandomAccessibleInterval<UnsignedByteType> prevInterval = hypershpereMask.getMaskInterval(
                new FinalInterval(prevStartMask, prevEndMask)
        );
        RandomAccessibleInterval<UnsignedByteType> currInterval = hypershpereMask.getMaskInterval(
                new FinalInterval(currStartMask, currEndMask)
        );
        Cursor<UnsignedByteType> prevMaskCursor = Views.flatIterable(prevInterval).cursor();
        Cursor<UnsignedByteType> currMaskCursor = Views.flatIterable(currInterval).cursor();
        Cursor<T> prevNeighbCursor = Views.flatIterable(prevNeighborhoodInterval).cursor();
        Cursor<T> curNeighbCursor = Views.flatIterable(currNeighborhoodInterval).cursor();
        while (prevMaskCursor.hasNext()) {
            int prevMaskPx = prevMaskCursor.next().get();
            int currMaskPx = currMaskCursor.next().get();
            T prevPx = prevNeighbCursor.next();
            T currPx = curNeighbCursor.next();
            if (prevMaskPx != 0 && currMaskPx == 0) {
                pixelHistogram.remove(prevPx);
            } else if (prevMaskPx == 0 && currMaskPx != 0) {
                pixelHistogram.add(currPx);
            }
        }
    }

    private void initializeHistogram(int axis) {
        pixelHistogram.clear();
        RandomAccess<T> sourceAccess = source.randomAccess();
        System.out.printf("Initialize: Center: %s, Min: %s, Max: %s\n",
                Arrays.toString(currentNeighborhoodRegion.center),
                Arrays.toString(currentNeighborhoodRegion.min),
                Arrays.toString(currentNeighborhoodRegion.max)
        );
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    int n = 0;
                    for (long r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, currentNeighborhoodRegion.tmpCoords);
                        T px = sourceAccess.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                        IntegerType<?> ipx = (IntegerType<?>)px;
                        if (ipx.getInteger() > 0) {
                            System.out.printf("INIT ADD: Center: %s, Point: %s, Value: %d\n",
                                    Arrays.toString(currentNeighborhoodRegion.center),
                                    Arrays.toString(currentNeighborhoodRegion.tmpCoords),
                                    ipx.getInteger()
                            );
                        }
                        if (currentNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                            pixelHistogram.add(px);
                            n++;
                        }
                    }
                    return n;
                },
                true
        );
    }

    private void updateHistogram(int axis) {
        RandomAccess<T> sourceAccess = source.randomAccess();
        System.out.printf("Update: Center: %s, Prev Center: %s\n",
                Arrays.toString(currentNeighborhoodRegion.center),
                Arrays.toString(currentNeighborhoodRegion.min),
                Arrays.toString(currentNeighborhoodRegion.max),
                Arrays.toString(prevNeighborhoodRegion.center),
                Arrays.toString(prevNeighborhoodRegion.min),
                Arrays.toString(prevNeighborhoodRegion.max)
        );
        currentNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    int n = 0;
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, currentNeighborhoodRegion.tmpCoords);
                        if (prevNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        if (currentNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                            T px = sourceAccess.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                            IntegerType<?> ipx = (IntegerType<?>) px;
                            if (ipx.getInteger() > 0) {
                                System.out.printf("ADD1: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d\n",
                                        Arrays.toString(prevNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.tmpCoords),
                                        ipx.getInteger()
                                );
                            }
                            pixelHistogram.add(px);
                            n++;
                        }

                        centerCoords[d] = -r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, currentNeighborhoodRegion.tmpCoords);
                        if (prevNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                            // point is both in the current and prev neighborhood so it was already considered
                            return n;
                        }
                        // new point found only in current neighborhood
                        if (currentNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                            T px = sourceAccess.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                            IntegerType<?> ipx = (IntegerType<?>) px;
                            if (ipx.getInteger() > 0) {
                                System.out.printf("ADD2: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d\n",
                                        Arrays.toString(prevNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.tmpCoords),
                                        ipx.getInteger()
                                );
                            }
                            pixelHistogram.add(px);
                            n++;
                        }
                    }
                    centerCoords[d] = 0;
                    CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, 1, currentNeighborhoodRegion.tmpCoords);
                    if (prevNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                        // center is both in the current and prev neighborhood so it was already considered
                        return n;
                    }
                    // center is only in current neighborhood
                    if (currentNeighborhoodRegion.contains(currentNeighborhoodRegion.tmpCoords)) {
                        T px = sourceAccess.setPositionAndGet(currentNeighborhoodRegion.tmpCoords);
                        IntegerType<?> ipx = (IntegerType<?>) px;
                        if (ipx.getInteger() > 0) {
                            System.out.printf("ADD3 %d: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d\n",
                                    d,
                                    Arrays.toString(prevNeighborhoodRegion.center),
                                    Arrays.toString(currentNeighborhoodRegion.center),
                                    Arrays.toString(currentNeighborhoodRegion.tmpCoords),
                                    ipx.getInteger()
                            );
                        }
                        pixelHistogram.add(px);
                        n++;
                    }
                    return n;
                },
                false
        );
        prevNeighborhoodRegion.scan(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    int n = 0;
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, prevNeighborhoodRegion.tmpCoords);
                        if (currentNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        if (prevNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                            T px = sourceAccess.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
                            IntegerType<?> ipx = (IntegerType<?>) px;
                            if (ipx.getInteger() > 0) {
                                System.out.printf("REMOVE1 %d: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d, ellipse prev: %f, ellipse curr: %f\n",
                                        d,
                                        Arrays.toString(prevNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.center),
                                        Arrays.toString(prevNeighborhoodRegion.tmpCoords),
                                        ipx.getInteger(),
                                        prevNeighborhoodRegion.ellipse(prevNeighborhoodRegion.tmpCoords, prevNeighborhoodRegion.center),
                                        currentNeighborhoodRegion.ellipse(prevNeighborhoodRegion.tmpCoords, currentNeighborhoodRegion.center)
                                );
                            }
                            pixelHistogram.remove(px);
                            n++;
                        }

                        centerCoords[d] = -r;
                        CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, prevNeighborhoodRegion.tmpCoords);
                        if (currentNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                            // point is both in the current and prev neighborhood so it can still be considered
                            return n;
                        }
                        // point only in prev neighborhood, so we shouldn't consider it anymore
                        if (prevNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                            T px = sourceAccess.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
                            IntegerType<?> ipx = (IntegerType<?>) px;
                            if (ipx.getInteger() > 0) {
                                System.out.printf("REMOVE2 %d: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d\n",
                                        d,
                                        Arrays.toString(prevNeighborhoodRegion.center),
                                        Arrays.toString(currentNeighborhoodRegion.center),
                                        Arrays.toString(prevNeighborhoodRegion.tmpCoords),
                                        ipx.getInteger()
                                );
                            }
                            pixelHistogram.remove(px);
                            n++;
                        }
                    }
                    centerCoords[d] = 0;
                    CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, 1, prevNeighborhoodRegion.tmpCoords);
                    if (currentNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                        // center is both in the current and prev neighborhood so it can still be considered
                        return n;
                    }
                    // center is only in prev neighborhood, so we shouldn't consider it anymore
                    if (prevNeighborhoodRegion.contains(prevNeighborhoodRegion.tmpCoords)) {
                        T px = sourceAccess.setPositionAndGet(prevNeighborhoodRegion.tmpCoords);
                        IntegerType<?> ipx = (IntegerType<?>) px;
                        if (ipx.getInteger() > 0) {
                            System.out.printf("REMOVE3 %d: Prev Center: %s, Curr Center: %s, Point: %s, Value: %d\n",
                                    d,
                                    Arrays.toString(prevNeighborhoodRegion.center),
                                    Arrays.toString(currentNeighborhoodRegion.center),
                                    Arrays.toString(prevNeighborhoodRegion.tmpCoords),
                                    ipx.getInteger()
                            );
                        }
                        pixelHistogram.remove(px);
                        n++;
                    }
                    return n;
                },
                true
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
