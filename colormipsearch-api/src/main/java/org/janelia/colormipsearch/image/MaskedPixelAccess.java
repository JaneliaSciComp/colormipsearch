package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;

import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccess;
import net.imglib2.type.Type;

public class MaskedPixelAccess<T extends Type<T>> extends AbstractConvertedRandomAccess<T, T> {

    /**
     * Predicate that defines the mask condition based on pixel's location and pixel value.
     */
    private final BiPredicate<long[], T> maskCond;
    /**
     * Value to use when the mask condition is met.
     */
    private final T zero;
    private final T foreground;
    private final long[] tmpPos;

    public MaskedPixelAccess(RandomAccess<T> source, BiPredicate<long[], T> maskCond, T zero, T foreground) {
        super(source);
        this.maskCond = maskCond;
        this.zero = zero;
        this.foreground = foreground;
        this.tmpPos = new long[source.numDimensions()];
    }

    private MaskedPixelAccess(MaskedPixelAccess<T> c) {
        super(c.source);
        this.maskCond = c.maskCond;
        this.zero = c.zero.copy();
        this.foreground = c.foreground != null ? c.foreground.copy() : null;
        this.tmpPos = c.tmpPos.clone();

    }

    @Override
    public T get() {
        source.localize(tmpPos);
        T v = source.get();
        if (maskCond.test(tmpPos, v)) {
            // mask condition is true
            return zero;
        } else {
            return foreground != null ? foreground : v;
        }
    }

    @Override
    public MaskedPixelAccess<T> copy() {
        return new MaskedPixelAccess<>(this);
    }
}
