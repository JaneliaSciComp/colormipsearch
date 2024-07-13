package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

/**
 * @param <T> pixel type
 */
public class MaxFilterRandomAccessibleInterval<T> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final int[] radii;
    private final Supplier<PixelHistogram<T>> slidingNeighborhoodHistogramSupplier;

    MaxFilterRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                      int[] radii,
                                      Supplier<PixelHistogram<T>> slidingNeighborhoodHistogramSupplier) {
        super(source);
        this.radii = radii;
        this.slidingNeighborhoodHistogramSupplier = slidingNeighborhoodHistogramSupplier;
    }

    @Override
    public MaxFilterRandomAccess<T> randomAccess() {
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(sourceInterval),
                Intervals.expand(sourceInterval, new FinalDimensions(radii))
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
                Intervals.expand(interval, new FinalDimensions(radii))
        );
        return new MaxFilterRandomAccess<>(
                extendedImg.randomAccess(),
                radii,
                slidingNeighborhoodHistogramSupplier.get()
        );
    }

}
