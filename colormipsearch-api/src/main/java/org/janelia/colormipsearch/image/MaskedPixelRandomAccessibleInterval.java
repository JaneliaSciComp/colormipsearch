package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;

/**
 * @param <T> pixel type
 */
public class MaskedPixelRandomAccessibleInterval<T extends IntegerType<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>>
                                                                           implements RandomAccessibleInterval<T> {

    private final BiPredicate<long[], T> maskCond;
    private final Supplier<T> unmaskedPixelSupplier;

    MaskedPixelRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                        BiPredicate<long[], T> maskCond,
                                        Supplier<T> unmaskedPixelSupplier) {
        super(source);
        this.maskCond = maskCond;
        this.unmaskedPixelSupplier = unmaskedPixelSupplier;
    }

    @Override
    public MaskedPixelAccess<T> randomAccess() {
        return new MaskedPixelAccess<>(
                sourceInterval.randomAccess(),
                maskCond,
                unmaskedPixelSupplier.get()
        );
    }

    @Override
    public MaskedPixelAccess<T>  randomAccess(Interval interval) {
        return new MaskedPixelAccess<>(
                sourceInterval.randomAccess(),
                maskCond,
                unmaskedPixelSupplier.get()
        );
    }

}
