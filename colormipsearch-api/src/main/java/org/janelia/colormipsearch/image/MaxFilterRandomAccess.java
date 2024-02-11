package org.janelia.colormipsearch.image;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.util.Intervals;
import org.apache.commons.collections4.CollectionUtils;

public class MaxFilterRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    /**
     * Stateful mechanism to update a value from another value.
     * @param <T>
     */
    public interface ValueUpdater<T> {
        /**
         * @param v
         * @return
         */
        T updateFrom(T v);
    }

    private final PixelHistogram<T> histogram;
    private final List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors;
    private final ValueUpdater<T> valueInitializer;
    private final ValueUpdater<T> valueUpdater;

    MaxFilterRandomAccess(RandomAccess<T> source,
                          PixelHistogram<T> histogram,
                          List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                          ValueUpdater<T> valueInitializer,
                          ValueUpdater<T> valueUpdater) {
        super(source);
        this.histogram = histogram;
        this.neighborhoodAccessors = neighborhoodAccessors;
        this.valueInitializer = valueInitializer;
        this.valueUpdater = valueUpdater;
    }

    private void updatePosition(Consumer<RandomAccess<T>> sourceAction, Consumer<RandomAccess<Neighborhood<T>>> neighborhoodAction) {
//        Map<RandomAccess<Neighborhood<T>>, Interval> naMapping = new HashMap<>();
//        long[] nMinMax = new long[2 * numDimensions()];
//        Map<Point, T> prevPoints = new HashMap<>();
//        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
//            Neighborhood<T> n = neighborhoodRandomAccess.setPositionAndGet(source);
//            for (int d = 0; d < numDimensions(); d++) {
//                nMinMax[d] = n.min(d);
//                nMinMax[numDimensions() + d] = n.max(d);
//            }
//            naMapping.put(neighborhoodRandomAccess, Intervals.createMinMax(nMinMax));
//            Cursor<T> nc = n.cursor();
//            while (nc.hasNext()) {
//                nc.fwd();
//                prevPoints.put(nc.positionAsPoint(), nc.get());
//            }
//        }

        sourceAction.accept(source);
        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
            neighborhoodAction.accept(neighborhoodRandomAccess);
        }

        histogram.clear();
//        Set<Point> currPoints = new HashSet<>();
        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
//            Interval prevInterval = naMapping.get(neighborhoodRandomAccess);
            Neighborhood<T> n = neighborhoodRandomAccess.setPositionAndGet(source);
            Cursor<T> nc = n.cursor();
            while (nc.hasNext()) {
                nc.fwd();
//                Point p = nc.positionAsPoint();
//                if (!prevPoints.containsKey(p)) {
                histogram.add(nc.get());
//                }
//                currPoints.add(nc.positionAsPoint());
            }
//            for (Point p : CollectionUtils.subtract(prevPoints.keySet(), currPoints)) {
//                histogram.remove(prevPoints.get(p));
//            }
//            System.out.printf("!!!! (%d,%d) [(%d,%d) (%d,%d)] -> [(%d,%d) (%d,%d)]\n",
//                    source.getIntPosition(0), source.getIntPosition(1),
//                    prevInterval.min(0), prevInterval.min(1), prevInterval.max(0), prevInterval.max(1),
//                    n.min(0), n.min(1), n.max(0), n.max(1));
        }
    }

    @Override
    public void fwd(final int d) {
        updatePosition(sa -> sa.fwd(d), na -> na.fwd(d));
    }

    @Override
    public void bck(final int d) {
        updatePosition(sa -> sa.bck(d), na -> na.bck(d));
    }

    @Override
    public void move(final int distance, final int d) {
        updatePosition(sa -> sa.move(distance, d), na -> na.move(distance, d));
    }

    @Override
    public void move(final long distance, final int d) {
        updatePosition(sa -> sa.move(distance, d), na -> na.move(distance, d));
    }

    @Override
    public void move(final Localizable localizable) {
        updatePosition(sa -> sa.move(localizable), na -> na.move(localizable));
    }

    @Override
    public void move(final int[] distance) {
        updatePosition(sa -> sa.move(distance), na -> na.move(distance));
    }

    @Override
    public void move(final long[] distance) {
        updatePosition(sa -> sa.move(distance), na -> na.move(distance));
    }

    @Override
    public void setPosition(final Localizable localizable) {
        updatePosition(sa -> sa.setPosition(localizable), na -> na.setPosition(localizable));
    }

    @Override
    public void setPosition(final int[] position) {
        updatePosition(sa -> sa.setPosition(position), na -> na.setPosition(position));
    }

    @Override
    public void setPosition(final long[] position) {
        updatePosition(sa -> sa.setPosition(position), na -> na.setPosition(position));
    }

    @Override
    public void setPosition(final int position, final int d) {
        updatePosition(sa -> sa.setPosition(position, d), na -> na.setPosition(position, d));
    }

    @Override
    public void setPosition(final long position, final int d) {
        updatePosition(sa -> sa.setPosition(position, d), na -> na.setPosition(position, d));
    }

    @Override
    public T get() {
        return valueInitializer.updateFrom(histogram.maxVal());
//        T currentValue = valueInitializer.updateFrom(source.get());
//        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
//            for (int d = 0; d < numDimensions(); d++) {
//                neighborhoodRandomAccess.setPosition(getLongPosition(d), d);
//            }
//            Neighborhood<T> neighborhood = neighborhoodRandomAccess.get();
//            Cursor<T> neighborhoodCursor = neighborhood.cursor();
//            while (neighborhoodCursor.hasNext()) {
//                currentValue = valueUpdater.updateFrom(neighborhoodCursor.next());
//            }
//        }
//        return currentValue;
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<T>(
                source.copy(),
                histogram.copy(),
                neighborhoodAccessors,
                valueInitializer,
                valueUpdater
        );
    }

}
