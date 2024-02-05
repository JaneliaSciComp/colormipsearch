package org.janelia.colormipsearch.image;

import java.util.function.BiFunction;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class BinaryPixelOpAccess<R, S, T> extends AbstractRectangularRandomAccess<T> {

    private final RandomAccess<R> s1;
    private final RandomAccess<S> s2;
    private final BiFunction<R, S, T> operator;

    public BinaryPixelOpAccess(RandomAccess<R> s1,
                               RandomAccess<S> s2,
                               Interval interval,
                               BiFunction<R, S, T> operator) {
        this(s1, s2, new RectIntervalHelper(interval), operator);
    }

    private BinaryPixelOpAccess(RandomAccess<R> s1,
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
        long[] tmpPos = new long[numDimensions()];
        super.localize(tmpPos);
        s1.setPosition(tmpPos);
        s2.setPosition(tmpPos);
        return operator.apply(s1.get(), s2.get());
    }

    @Override
    public BinaryPixelOpAccess<R, S, T> copy() {
        return new BinaryPixelOpAccess<>(s1.copy(), s2.copy(), rectIntervalHelper.copy(), operator);
    }
}
