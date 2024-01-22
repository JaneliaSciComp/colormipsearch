package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;

import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccess;

public class MaskedPixelAccess<T> extends AbstractConvertedRandomAccess<T, T> {

    private final BiPredicate<long[], T> maskCond;
    private final T zero;
    private final long[] tmpPos;

    public MaskedPixelAccess(RandomAccess<T> source, BiPredicate<long[], T> maskCond, T zero) {
        super(source);
        this.maskCond = maskCond;
        this.zero = zero;
        this.tmpPos = new long[source.numDimensions()];
    }

    @Override
    public T get() {
        source.localize(tmpPos);
        T v = source.get();
        if (maskCond.test(tmpPos, v)) {
            return zero;
        } else {
            return v;
        }
    }

    @Override
    public MaskedPixelAccess<T> copy() {
        return new MaskedPixelAccess<>(source.copy(), maskCond, zero);
    }

    public T getZero() {
        return zero;
    }
}
