package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;

public class SimpleWrapperAccessAdapter<T> implements ImageAccess<T> {

    private final IterableRandomAccess<T> sourceAccess;
    private final Interval interval;
    private final T backgroundValue;

    public SimpleWrapperAccessAdapter(IterableRandomAccess<T> sourceAccess,
                                      Interval interval,
                                      T backgroundValue) {
        this.sourceAccess = sourceAccess;
        this.interval = interval;
        this.backgroundValue = backgroundValue;
    }

    @Override
    public Interval getInterval() {
        return interval;
    }

    @Override
    public RandomAccess<T> getRandomAccess() {
        return sourceAccess;
    }

    @Override
    public Cursor<T> getCursor() {
        return sourceAccess;
    }

    @Override
    public long getSize() {
        return sourceAccess.getSize();
    }

    @Override
    public T getBackgroundValue() {
        return backgroundValue;
    }
}
