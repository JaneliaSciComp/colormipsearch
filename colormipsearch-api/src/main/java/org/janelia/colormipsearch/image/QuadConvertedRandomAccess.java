package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccess;

public class QuadConvertedRandomAccess<P, Q, R, S, T> extends AbstractConvertedRandomAccess<P, T> {

    private final RandomAccess<Q> s2;
    private final RandomAccess<R> s3;
    private final RandomAccess<S> s4;
    private final Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> converterSupplier;
    private final QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T> converter;
    private final Supplier<? super T> convertedSupplier;
    private final T converted;

    QuadConvertedRandomAccess(RandomAccess<P> s1,
                              RandomAccess<Q> s2,
                              RandomAccess<R> s3,
                              RandomAccess<S> s4,
                              Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> converterSupplier,
                              Supplier<? super T> convertedSupplier) {
        super(s1);
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.converterSupplier = converterSupplier;
        this.converter = converterSupplier.get();
        this.convertedSupplier = convertedSupplier;
        this.converted = (T) convertedSupplier.get();
    }

    @Override
    public void fwd(final int d) {
        source.fwd(d);
        s2.fwd(d);
        s3.fwd(d);
        s4.fwd(d);
    }

    @Override
    public void bck(final int d) {
        source.bck(d);
        s2.bck(d);
        s3.bck(d);
        s4.bck(d);
    }

    @Override
    public void move(final int distance, final int d) {
        source.move(distance, d);
        s2.move(distance, d);
        s3.move(distance, d);
        s4.move(distance, d);
    }

    @Override
    public void move(final long distance, final int d) {
        source.move(distance, d);
        s2.move(distance, d);
        s3.move(distance, d);
        s4.move(distance, d);
    }

    @Override
    public void move(final Localizable localizable) {
        source.move(localizable);
        s2.move(localizable);
        s3.move(localizable);
        s4.move(localizable);
    }

    @Override
    public void move(final int[] distance) {
        source.move(distance);
        s2.move(distance);
        s3.move(distance);
        s4.move(distance);
    }

    @Override
    public void move(final long[] distance) {
        source.move(distance);
        s2.move(distance);
        s3.move(distance);
        s4.move(distance);
    }

    @Override
    public void setPosition(final Localizable localizable) {
        source.setPosition(localizable);
        s2.setPosition(localizable);
        s3.setPosition(localizable);
        s4.setPosition(localizable);
    }

    @Override
    public void setPosition(final int[] position) {
        source.setPosition(position);
        s2.setPosition(position);
        s3.setPosition(position);
        s4.setPosition(position);
    }

    @Override
    public void setPosition(final long[] position) {
        source.setPosition(position);
        s2.setPosition(position);
        s3.setPosition(position);
        s4.setPosition(position);
    }

    @Override
    public void setPosition(final int position, final int d) {
        source.setPosition(position, d);
        s2.setPosition(position, d);
        s3.setPosition(position, d);
        s4.setPosition(position, d);
    }

    @Override
    public void setPosition(final long position, final int d) {
        source.setPosition(position, d);
        s2.setPosition(position, d);
        s3.setPosition(position, d);
        s4.setPosition(position, d);
    }

    @Override
    public T get() {
        converter.convert(
                source.get(),
                s2.get(),
                s3.get(),
                s4.get(),
                converted
        );
        return converted;
    }

    @Override
    public QuadConvertedRandomAccess<P, Q, R, S, T> copy() {
        return new QuadConvertedRandomAccess<>(
                source.copy(),
                s2.copy(),
                s3.copy(),
                s4.copy(),
                converterSupplier,
                convertedSupplier);
    }
}
