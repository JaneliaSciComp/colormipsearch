package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

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
        return new MaxFilterRandomAccess<>(
                sourceInterval.randomAccess(),
                sourceInterval, radii,
                slidingNeighborhoodHistogramSupplier.get()
        );
    }

    @Override
    public MaxFilterRandomAccess<T>  randomAccess(Interval interval) {
        return new MaxFilterRandomAccess<>(
                sourceInterval.randomAccess(interval),
                interval, radii,
                slidingNeighborhoodHistogramSupplier.get()
        );
    }

}
