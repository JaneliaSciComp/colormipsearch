package org.janelia.colormipsearch.image;

import java.util.List;
import java.util.stream.Collectors;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public class MaxFilterRandomAccess<T> extends AbstractRectangularRandomAccess<T> {

    /**
     * Stateful mechanism to update a value from another value.
     * @param <T>
     */
    public interface StatefullValueUpdate<T> {
        /**
         * @param v
         * @return
         */
        T updateFrom(T v);
    }

    private final RandomAccess<T> source;
    private final List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors;
    private final List<HistogramWithPixelLocations<T>> pixelHistograms;
    private final StatefullValueUpdate<T> valueInitializer;
    private final StatefullValueUpdate<T> valueUpdater;

    public MaxFilterRandomAccess(RandomAccess<T> source,
                                 Interval interval,
                                 List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                 List<HistogramWithPixelLocations<T>> pixelHistograms,
                                 StatefullValueUpdate<T> valueInitializer,
                                 StatefullValueUpdate<T> valueUpdater) {
        this(source, new RectIntervalHelper(interval), neighborhoodAccessors, pixelHistograms, valueInitializer, valueUpdater);
    }

    private MaxFilterRandomAccess(RandomAccess<T> source,
                                  RectIntervalHelper coordsHelper,
                                  List<RandomAccess<Neighborhood<T>>> neighborhoodAccessors,
                                  List<HistogramWithPixelLocations<T>> pixelHistograms,
                                  StatefullValueUpdate<T> valueInitializer,
                                  StatefullValueUpdate<T> valueUpdater) {
        super(coordsHelper);
        this.source = source;
        this.valueInitializer = valueInitializer;
        this.valueUpdater = valueUpdater;
        this.neighborhoodAccessors = neighborhoodAccessors;
        this.pixelHistograms = pixelHistograms;
    }

    @Override
    public T get() {
        localize(tmpPos);
        T currentValue = valueInitializer.updateFrom(source.get());
        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
            neighborhoodRandomAccess.setPosition(tmpPos);
            Neighborhood<T> neighborhood = neighborhoodRandomAccess.get();
            Cursor<T> neighborhoodCursor = neighborhood.cursor();
            while (neighborhoodCursor.hasNext()) {
                currentValue = valueUpdater.updateFrom(neighborhoodCursor.next());
            }
        }
        return currentValue;

//            return neighborhoodAccessors.stream()
//                .map(neighborhoodRandomAccess -> neighborhoodRandomAccess.setPositionAndGet(tmpPos))
//                .flatMap(neighborhood -> neighborhood.stream())
//                .reduce(currentValue, valueSelector::maxOf)
//                ;

//        return IntStream.range(0, neighborhoodAccessors.size())
//                .mapToObj(i -> ImmutablePair.of(neighborhoodAccessors.get(i), pixelHistograms.get(i)))
//                .flatMap(neighborhoodWithHistogram -> {
//                    RandomAccess<Neighborhood<T>> neighborhoodRandomAccess = neighborhoodWithHistogram.getLeft();
//                    HistogramWithPixelLocations<T> currentNeighborhoodHistogram = neighborhoodWithHistogram.getRight();
//                    Neighborhood<T> neighborhood = neighborhoodRandomAccess.setPositionAndGet(tmpPos);
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
                valueInitializer,
                valueUpdater
        );
    }

}
