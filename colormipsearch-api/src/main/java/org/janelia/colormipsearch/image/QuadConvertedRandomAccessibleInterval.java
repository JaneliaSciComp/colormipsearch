package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

public class QuadConvertedRandomAccessibleInterval<P, Q, R, S, T> extends AbstractWrappedInterval<RandomAccessibleInterval<P>> implements RandomAccessibleInterval<T> {

    private final RandomAccessibleInterval<Q> s2;
    private final RandomAccessibleInterval<R> s3;
    private final RandomAccessibleInterval<S> s4;
    private final Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> converterSupplier;
    private final Supplier<? extends T> convertedSupplier;

    QuadConvertedRandomAccessibleInterval(RandomAccessibleInterval<P> s1,
                                          RandomAccessibleInterval<Q> s2,
                                          RandomAccessibleInterval<R> s3,
                                          RandomAccessibleInterval<S> s4,
                                          Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> converterSupplier,
                                          Supplier<? extends T> convertedSupplier) {
        super(s1);
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.converterSupplier = converterSupplier;
        this.convertedSupplier = convertedSupplier;
    }

    @Override
    public QuadConvertedRandomAccess<P, Q, R, S, T> randomAccess() {
        return new QuadConvertedRandomAccess<>(
                sourceInterval.randomAccess(),
                s2.randomAccess(),
                s3.randomAccess(),
                s4.randomAccess(),
                converterSupplier,
                convertedSupplier
        );
    }

    @Override
    public QuadConvertedRandomAccess<P, Q, R, S, T>  randomAccess(Interval interval) {
        return new QuadConvertedRandomAccess<>(
                sourceInterval.randomAccess(),
                s2.randomAccess(interval),
                s3.randomAccess(interval),
                s4.randomAccess(interval),
                converterSupplier,
                convertedSupplier
        );
    }


}
