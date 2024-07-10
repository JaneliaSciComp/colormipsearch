package org.janelia.colormipsearch.image;

import java.util.function.Function;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.Type;
import net.imglib2.view.IterableRandomAccessibleInterval;
import net.imglib2.view.RandomAccessibleIntervalCursor;

public class SimpleImageAccess<T extends Type<T>> extends IterableRandomAccessibleInterval<T> implements ImageAccess<T> {

    private final Function<RandomAccess<T>, RandomAccess<T>> randomAccessSupplier;
    private final T backgroundValue;

    public SimpleImageAccess(Img<T> sourceImg) {
        this(sourceImg, sourceImg.factory().type());
    }

    public SimpleImageAccess(RandomAccessibleInterval<T> sourceAccess,
                             T backgroundValue) {
        this(sourceAccess, Function.identity(), backgroundValue);
    }

    public SimpleImageAccess(RandomAccessibleInterval<T> sourceAccess,
                             Function<RandomAccess<T>, RandomAccess<T>> randomAccessSupplier,
                             T backgroundValue) {
        super(sourceAccess);
        this.randomAccessSupplier = randomAccessSupplier;
        this.backgroundValue = backgroundValue;
    }

    @Override
    public Cursor<T> cursor() {
        return new RandomAccessibleIntervalCursor<T>(this);
    }

    @Override
    public RandomAccess<T> randomAccess() {
        return randomAccessSupplier.apply(super.randomAccess());
    }

    @Override
    public RandomAccess<T> randomAccess(Interval i) {
        return randomAccessSupplier.apply(super.randomAccess(i));
    }

    @Override
    public T getBackgroundValue() {
        return backgroundValue;
    }
}
