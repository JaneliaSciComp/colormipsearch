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

    private final RandomAccess<T> source;
    private final List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors;
    private final ValueSelector<T> valueSelector;

    public MaxFilterRandomAccess(RandomAccess<T> source,
                                 Interval interval,
                                 List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                 ValueSelector<T> valueSelector) {
        this(source, new RectIntervalHelper(interval), neighborhoodAccessors, valueSelector);
    }

    private MaxFilterRandomAccess(RandomAccess<T> source,
                                  RectIntervalHelper coordsHelper,
                                  List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                  ValueSelector<T> valueSelector) {
        super(coordsHelper);
        this.source = source;
        this.valueSelector = valueSelector;
        this.neighborhoodAccessors = neighborhoodAccessors;
    }

    @Override
    public T get() {
        localize(tmpPos);
        T currentValue = source.get();
        return neighborhoodAccessors.stream()
                .flatMap(neighborhoodAccess -> neighborhoodAccess.setPositionAndGet(tmpPos).stream())
                .reduce(currentValue, valueSelector::maxOf);
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<>(
                source.copy(),
                rectIntervalHelper.copy(),
                neighborhoodAccessors,
                valueSelector
        );
    }

}
