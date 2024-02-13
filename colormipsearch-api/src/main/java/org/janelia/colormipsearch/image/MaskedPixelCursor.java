package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedCursor;
import net.imglib2.converter.AbstractConvertedRandomAccess;

public class MaskedPixelCursor<T> extends AbstractConvertedCursor<T, T> {

    /**
     * Predicate that defines the mask condition based on pixel's location and pixel value.
     */
    private final BiPredicate<long[], T> maskCond;
    /**
     * Value to use for a masked pixel.
     */
    private final T zero;
    private final long[] tmpPos;

    public MaskedPixelCursor(Cursor<T> source, BiPredicate<long[], T> maskCond, T zero) {
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
    public MaskedPixelCursor<T> copy() {
        return new MaskedPixelCursor<>(source.copy(), maskCond, zero);
    }

    public T getZero() {
        return zero;
    }
}
