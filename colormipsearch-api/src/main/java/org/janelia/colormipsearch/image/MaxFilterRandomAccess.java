package org.janelia.colormipsearch.image;

import java.util.List;
import java.util.stream.Collectors;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

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
    private final List<HistogramWithPixelLocations<T>> pixelHistograms;
    private final ValueSelector<T> valueSelector;

    public MaxFilterRandomAccess(RandomAccess<T> source,
                                 Interval interval,
                                 List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                 List<HistogramWithPixelLocations<T>> pixelHistograms,
                                 ValueSelector<T> valueSelector) {
        this(source, new RectIntervalHelper(interval), neighborhoodAccessors, pixelHistograms, valueSelector);
    }

    private MaxFilterRandomAccess(RandomAccess<T> source,
                                  RectIntervalHelper coordsHelper,
                                  List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                  List<HistogramWithPixelLocations<T>> pixelHistograms,
                                  ValueSelector<T> valueSelector) {
        super(coordsHelper);
        this.source = source;
        this.valueSelector = valueSelector;
        this.neighborhoodAccessors = neighborhoodAccessors;
        this.pixelHistograms = pixelHistograms;
    }

    @Override
    public T get() {
        long[] pos = positionAsLongArray();
        T currentValue = source.get();
        return neighborhoodAccessors.stream().parallel()
                .map(neighborhoodRandomAccess -> neighborhoodRandomAccess.setPositionAndGet(pos))
                .flatMap(neighborhood -> neighborhood.stream().distinct())
                .reduce(currentValue, valueSelector::maxOf);

//        return IntStream.range(0, neighborhoodAccessors.size())
//                .mapToObj(i -> ImmutablePair.of(neighborhoodAccessors.get(i), pixelHistograms.get(i)))
//                .flatMap(neighborhoodWithHistogram -> {
//                    RandomAccess<Neighborhood<T>> neighborhoodRandomAccess = neighborhoodWithHistogram.getLeft();
//                    HistogramWithPixelLocations<T> currentNeighborhoodHistogram = neighborhoodWithHistogram.getRight();
//                    Neighborhood<T> neighborhood = neighborhoodRandomAccess.setPositionAndGet(pos);
//                    long[] neighborhoodMinMax = LongStream.concat(
//                            Arrays.stream(neighborhood.minAsLongArray()),
//                            Arrays.stream(neighborhood.maxAsLongArray())
//                    ).toArray();
//                    Interval neighborhoodInterval = Intervals.createMinMax(neighborhoodMinMax);
//                    Interval prevNeighborhoodInterval = currentNeighborhoodHistogram.updateInterval(neighborhoodInterval);
//                    // remove pixels that are in (prevNeighborhood - currentNeighborhood)
//                    Set<Point> toRemove = Views.iterable(Intervals.positions(prevNeighborhoodInterval)).stream()
//                            .filter(ls -> !Intervals.contains(neighborhoodInterval, ls))
//                            .map(ls -> ls.positionAsPoint())
//                            .collect(Collectors.toSet());
//                    currentNeighborhoodHistogram.remove(toRemove);
//                    // add new pixels from currentNeighborhood, i.e., pixels from (currentNeighborhood - prevNeighborhood)
//                    return ImageAccessUtils.stream(neighborhood.cursor(), false)
//                            .filter(ls -> !Intervals.contains(prevNeighborhoodInterval, ls.positionAsPoint()))
//                            .map(ls -> currentNeighborhoodHistogram.add(ls.positionAsPoint(), ls.get()))
//                            ;
//                })
//                .reduce(currentValue, valueSelector::maxOf)
//                ;
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<T>(
                source.copy(),
                rectIntervalHelper.copy(),
                neighborhoodAccessors,
                pixelHistograms.stream().map(HistogramWithPixelLocations::copy).collect(Collectors.toList()),
                valueSelector
        );
    }

}
