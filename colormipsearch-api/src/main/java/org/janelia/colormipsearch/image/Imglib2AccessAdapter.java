package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;

public class Imglib2AccessAdapter<T> implements ImageAccess<T> {

    private final Img<T> sourceImage;
    private final T backgroundValue;

    public Imglib2AccessAdapter(Img<T> sourceImage, T backgroundValue) {
        this.sourceImage = sourceImage;
        this.backgroundValue = backgroundValue;
    }

    @Override
    public Interval getInterval() {
        return sourceImage;
    }

    @Override
    public RandomAccess<T> getRandomAccess() {
        return sourceImage.randomAccess();
    }

    @Override
    public Cursor<T> getCursor() {
        return sourceImage.cursor();
    }

    @Override
    public long getSize() {
        return sourceImage.size();
    }

    @Override
    public T getBackgroundValue() {
        return backgroundValue;
    }
}
