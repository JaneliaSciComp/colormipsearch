package org.janelia.colormipsearch.image;

import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.logic.BitType;

public class MaxFilterRandomAccess<T> extends AbstractRectangularRandomAccess<T> {

    public interface ValueSelector<T> {
        /**
         * The method returns the max if two values but it may be a bit more complicated.
         * For example for RGB values it may return a new value that has the max value from each channel, i.e.,
         * RGB(max(r1,r2), max(g1,g2), max(b1,b2))
         * @param v1
         * @param v2
         * @return
         */
        T maxOf(T v1, T v2);
    }

    public interface ValueUpdater<T> {
        void update(long[] pos, T newVal);
    }

    private final RandomAccess<T> source;
    private final List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors;
    private final ValueSelector<T> valueSelector;
    private final ValueUpdater<T> valueUpdater;
    private final long[] posToUpdate;

    public MaxFilterRandomAccess(RandomAccess<T> source,
                                 Interval interval,
                                 List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                 ValueSelector<T> valueSelector,
                                 ValueUpdater<T> valueUpdater) {
        this(source, new RectIntervalHelper(interval), neighborhoodAccessors, valueSelector, valueUpdater);
    }

    private MaxFilterRandomAccess(RandomAccess<T> source,
                                  RectIntervalHelper coordsHelper,
                                  List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                  ValueSelector<T> valueSelector,
                                  ValueUpdater<T> valueUpdater) {
        super(coordsHelper);
        this.source = source;
        this.valueSelector = valueSelector;
        this.neighborhoodAccessors = neighborhoodAccessors;
        this.valueUpdater = valueUpdater;
        this.posToUpdate = new long[source.numDimensions()];
    }

    @Override
    public T get() {
        localize(tmpPos);
        T currentValue = source.get();
        T newValue = neighborhoodAccessors.stream()
                .flatMap(neighborhoodAccess -> neighborhoodAccess.setPositionAndGet(tmpPos).stream())
                .reduce(currentValue, valueSelector::maxOf);
//        if (!currentValue.equals(newValue)) {
//            neighborhoodAccessors.stream()
//                    .map(na -> na.setPositionAndGet(tmpPos))
//                    .forEach(n -> {
//                        Cursor<T> nc = n.cursor();
//                        while (nc.hasNext()) {
//                            nc.fwd();
//                            nc.localize(posToUpdate);
//                            valueUpdater.update(posToUpdate, newValue);
//                        }
//                    });
//        }
        return newValue;
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<>(
                source.copy(),
                rectIntervalHelper.copy(),
                neighborhoodAccessors,
                valueSelector,
                valueUpdater
        );
    }

}
