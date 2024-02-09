package org.janelia.colormipsearch.image;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

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

    MaxFilterRandomAccess(RandomAccess<T> source,
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
//        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
//            neighborhoodRandomAccess.setPosition(tmpPos);
//            Neighborhood<T> neighborhood = neighborhoodRandomAccess.get();
//            Cursor<T> neighborhoodCursor = neighborhood.cursor();
//            while (neighborhoodCursor.hasNext()) {
//                currentValue = valueUpdater.updateFrom(neighborhoodCursor.next());
//            }
//        }
//        return currentValue;

        long[] neighborhoodMinMax = new long[2*numDimensions()];
        for (int i = 0; i < neighborhoodAccessors.size(); i++) {
            RandomAccess<Neighborhood<T>> neighborhoodRandomAccess = neighborhoodAccessors.get(i);
            HistogramWithPixelLocations<T> currentNeighborhoodHistogram = pixelHistograms.get(i);
            Neighborhood<T> neighborhood = neighborhoodRandomAccess.setPositionAndGet(tmpPos);
            for (int d = 0; d < numDimensions(); d++) {
                neighborhoodMinMax[d] = neighborhood.min(d);
                neighborhoodMinMax[d + numDimensions()] = neighborhood.max(d);
            }
            Interval neighborhoodInterval = Intervals.createMinMax(neighborhoodMinMax);
            Interval prevNeighborhoodInterval = currentNeighborhoodHistogram.updateInterval(neighborhoodInterval);
            Interval intersection = Intervals.intersect(neighborhoodInterval, prevNeighborhoodInterval);
            Set<Point> toRemove = Views.iterable(Intervals.positions(prevNeighborhoodInterval)).stream()
                    .filter(ls -> !Intervals.contains(intersection, ls))
                    .map(ls -> ls.positionAsPoint())
                    .collect(Collectors.toSet());
            valueUpdater.updateFrom(currentNeighborhoodHistogram.remove(toRemove));
            Cursor<T> neighborhoodCursor = neighborhood.cursor();
            while (neighborhoodCursor.hasNext()) {
                neighborhoodCursor.fwd();
                if (!Intervals.contains(prevNeighborhoodInterval, neighborhoodCursor)) {
                    currentValue = valueUpdater.updateFrom(
                            currentNeighborhoodHistogram.add(neighborhoodCursor.positionAsPoint(), neighborhoodCursor.get())
                    );
                }
            }
        }
        return currentValue;
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
