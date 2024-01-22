package org.janelia.colormipsearch.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public class MaxFilterRandomAccess<T> extends AbstractRectangularRandomAccess<T> {

    public interface MaxOf<T> {
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
    private final int radius;
//    private final Map<Long, Neighborhood<T>> neighborhoods;
    private final MaxOf<T> valueComparator;
    private final HyperSphereNeighborhoodFactory<T> neighborhoodFactory;

    public MaxFilterRandomAccess(RandomAccess<T> source,
                                 Interval interval,
                                 int radius,
                                 MaxOf<T> valueComparator) {
        this(source, new RectIntervalHelper(interval), radius, valueComparator);
    }

    private MaxFilterRandomAccess(RandomAccess<T> source,
                                  RectIntervalHelper coordsHelper,
                                  int radius,
                                  MaxOf<T> valueComparator) {
        super(coordsHelper);
        this.source = source;
        this.radius = radius;
        this.valueComparator = valueComparator;
        this.neighborhoodFactory = HyperSphereNeighborhood.factory();
    }

    @Override
    public T get() {
        localize(tmpPos);
        T currentValue = source.setPositionAndGet(tmpPos);
        Neighborhood<T> neighborhood = neighborhoodFactory.create(tmpPos, radius, source);
        for (T v : neighborhood) {
            currentValue = valueComparator.maxOf(v, currentValue);
        }
        return currentValue;
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<>(source.copy(), rectIntervalHelper.copy(), radius, valueComparator);
    }

    private  Map<Long, Neighborhood<T>> indexNeighborhoods(Iterable<Neighborhood<T>> neighborhoods) {
        return StreamSupport.stream(neighborhoods.spliterator(), false)
                .collect(Collectors.toMap(
                        n -> rectIntervalHelper.rectCoordsToLinearIndex(n.positionAsLongArray()),
                        n -> n));
    }

}
