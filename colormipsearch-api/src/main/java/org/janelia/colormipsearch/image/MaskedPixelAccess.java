package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class MaskedPixelAccess<T> extends AbstractIterablePositionableAccess<T> {

    private final RandomAccess<T> source;
    private final BiPredicate<long[], T> maskCond;
    private final T zero;

    public MaskedPixelAccess(RandomAccess<T> source, Interval interval, BiPredicate<long[], T> maskCond, T zero) {
        this(source, new RectCoordsHelper(interval), maskCond, zero);
    }

    private MaskedPixelAccess(RandomAccess<T> source, RectCoordsHelper coordsHelper, BiPredicate<long[], T> maskCond, T zero) {
        super(coordsHelper);
        this.source = source;
        this.maskCond = maskCond;
        this.zero = zero;
    }

    @Override
    public T get() {
        super.localize(tmpPos);
        T v = source.setPositionAndGet(tmpPos);
        if (maskCond.test(tmpPos, v)) {
            return zero;
        } else {
            return v;
        }
    }

    @Override
    public void fwd() {
        while (super.hasNext()) {
            super.fwd();
            super.localize(tmpPos);
            T v = source.setPositionAndGet(tmpPos);
            if (maskCond.negate().test(tmpPos, v))
                break;
        }
    }

    @Override
    public MaskedPixelAccess<T> copy() {
        return new MaskedPixelAccess<>(source.copy(), coordsHelper.copy(), maskCond, zero);
    }

}
