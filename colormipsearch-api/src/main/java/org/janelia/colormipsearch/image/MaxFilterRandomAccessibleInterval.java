package org.janelia.colormipsearch.image;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.minmax.HyperSphereShape;

public class MaxFilterRandomAccessibleInterval<T> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final long[] radii;
    private final Shape strel;
    private final PixelHistogram<T> slidingNeighborhoodHistogram;

    MaxFilterRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                      long[] radii,
                                      PixelHistogram<T> slidingNeighborhoodHistogram) {
        super(source);
        this.radii = radii;
        this.strel = new HyperSphereShape(radii, slidingNeighborhoodHistogram);
        this.slidingNeighborhoodHistogram = slidingNeighborhoodHistogram;
    }

    @Override
    public MaxFilterRandomAccess<T> randomAccess() {
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(sourceInterval),
                Intervals.expand(sourceInterval, radii)
        );
        return new MaxFilterRandomAccess<>(
                sourceInterval.randomAccess(),
                strel.neighborhoodsRandomAccessible(extendedImg).randomAccess(),
                slidingNeighborhoodHistogram
        );
    }

    @Override
    public MaxFilterRandomAccess<T>  randomAccess(Interval interval) {
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(sourceInterval),
                Intervals.expand(interval, radii));
        return new MaxFilterRandomAccess<>(
                sourceInterval.randomAccess(interval),
                strel.neighborhoodsRandomAccessible(extendedImg).randomAccess(interval),
                slidingNeighborhoodHistogram
        );
    }

}
