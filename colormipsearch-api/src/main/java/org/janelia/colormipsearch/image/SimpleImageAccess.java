package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;

public class SimpleImageAccess<T> extends RectangularAccessIterableInterval<T> implements ImageAccess<T> {

    private final T backgroundValue;

    public SimpleImageAccess(RandomAccess<T> sourceAccess,
                             Interval interval,
                             T backgroundValue) {
        super(sourceAccess, interval);
        this.backgroundValue = backgroundValue;
    }

    public SimpleImageAccess(RandomAccessibleInterval<T> sourceAccess, T backgroundValue) {
        super(sourceAccess);
        this.backgroundValue = backgroundValue;
    }

    public SimpleImageAccess(ImageAccess<T> sourceAccess) {
        super(sourceAccess);
        this.backgroundValue = sourceAccess.getBackgroundValue();
    }

    @Override
    public T getBackgroundValue() {
        return backgroundValue;
    }
}
