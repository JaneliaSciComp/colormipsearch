package org.janelia.colormipsearch.image;

import java.util.function.BiFunction;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class PixelOpAccess<R, S, T> extends AbstractRectangularRandomAccess<T> {

    private final RandomAccess<R> s1;
    private final RandomAccess<S> s2;
    private final BiFunction<R, S, T> operator;

    public PixelOpAccess(RandomAccess<R> s1,
                         RandomAccess<S> s2,
                         Interval interval,
                         BiFunction<R, S, T> operator) {
        this(s1, s2, new RectIntervalHelper(interval), operator);
    }

    private PixelOpAccess(RandomAccess<R> s1,
                          RandomAccess<S> s2,
                          RectIntervalHelper rectIntervalHelper,
                          BiFunction<R, S, T> operator) {
        super(rectIntervalHelper);
        assert rectIntervalHelper.numDimensions() == s1.numDimensions();
        assert rectIntervalHelper.numDimensions() == s2.numDimensions();
        this.s1 = s1;
        this.s2 = s2;
        this.operator = operator;
    }

    @Override
    public T get() {
        super.localize(tmpPos);
        R v1 = s1.setPositionAndGet(tmpPos);
        S v2 = s2.setPositionAndGet(tmpPos);
        return operator.apply(v1, v2);
    }

    @Override
    public PixelOpAccess<R, S, T> copy() {
        return new PixelOpAccess<>(s1.copy(), s2.copy(), rectIntervalHelper.copy(), operator);
    }
}
