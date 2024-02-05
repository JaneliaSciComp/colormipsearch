package org.janelia.colormipsearch.image;

import java.util.function.BiFunction;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class QuadPixelOpAccess<P, Q, R, S, T> extends AbstractRectangularRandomAccess<T> {

    private final RandomAccess<P> s1;
    private final RandomAccess<Q> s2;
    private final RandomAccess<R> s3;
    private final RandomAccess<S> s4;
    private final ImageTransforms.QuadTupleFunction<P, Q, R, S, T> operator;

    public QuadPixelOpAccess(RandomAccess<P> s1,
                             RandomAccess<Q> s2,
                             RandomAccess<R> s3,
                             RandomAccess<S> s4,
                             Interval interval,
                             ImageTransforms.QuadTupleFunction<P, Q, R, S, T> operator) {
        this(s1, s2, s3, s4, new RectIntervalHelper(interval), operator);
    }

    private  QuadPixelOpAccess(RandomAccess<P> s1,
                               RandomAccess<Q> s2,
                               RandomAccess<R> s3,
                               RandomAccess<S> s4,
                               RectIntervalHelper rectIntervalHelper,
                               ImageTransforms.QuadTupleFunction<P, Q, R, S, T> operator) {
        super(rectIntervalHelper);
        assert rectIntervalHelper.numDimensions() == s1.numDimensions();
        assert rectIntervalHelper.numDimensions() == s2.numDimensions();
        assert rectIntervalHelper.numDimensions() == s3.numDimensions();
        assert rectIntervalHelper.numDimensions() == s4.numDimensions();
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.operator = operator;
    }

    @Override
    public T get() {
        long[] tmpPos = new long[numDimensions()];
        super.localize(tmpPos);
        s1.setPosition(tmpPos);
        s2.setPosition(tmpPos);
        s3.setPosition(tmpPos);
        s4.setPosition(tmpPos);
        return operator.apply(s1.get(), s2.get(), s3.get(), s4.get());
    }

    @Override
    public QuadPixelOpAccess<P, Q, R, S, T> copy() {
        return new QuadPixelOpAccess<>(
                s1.copy(),
                s2.copy(),
                s3.copy(),
                s4.copy(),
                rectIntervalHelper.copy(),
                operator);
    }
}
