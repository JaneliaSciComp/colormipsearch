package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class MaxFilterRandomAccessibleInterval<T> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final long[] radii;
    private final Supplier<PixelHistogram<T>> slidingNeighborhoodHistogramSupplier;

    MaxFilterRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                      long[] radii,
                                      Supplier<PixelHistogram<T>> slidingNeighborhoodHistogramSupplier) {
        super(source);
        this.radii = radii;
        this.slidingNeighborhoodHistogramSupplier = slidingNeighborhoodHistogramSupplier;
    }

    @Override
    public MaxFilterRandomAccess<T> randomAccess() {
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(sourceInterval),
                Intervals.expand(sourceInterval, radii)
        );
        return new MaxFilterRandomAccess<>(
                extendedImg.randomAccess(),
                radii,
                slidingNeighborhoodHistogramSupplier.get()
        );
    }

    @Override
    public MaxFilterRandomAccess<T>  randomAccess(Interval interval) {
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(sourceInterval),
                Intervals.expand(interval, radii)
        );
        return new MaxFilterRandomAccess<>(
                extendedImg.randomAccess(),
                radii,
                slidingNeighborhoodHistogramSupplier.get()
        );
    }

}
