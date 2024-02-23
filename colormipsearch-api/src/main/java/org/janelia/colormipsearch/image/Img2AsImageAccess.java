package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;

public class Img2AsImageAccess<T> implements ImageAccess<T> {

    private final Img<T> sourceImg;

    public Img2AsImageAccess(Img<T> sourceImg) {
        this.sourceImg = sourceImg;
    }

    @Override
    public T getBackgroundValue() {
        return sourceImg.factory().type();
    }

    @Override
    public Cursor<T> cursor() {
        return sourceImg.cursor();
    }

    @Override
    public Cursor<T> localizingCursor() {
        return sourceImg.localizingCursor();
    }

    @Override
    public long size() {
        return sourceImg.size();
    }

    @Override
    public Object iterationOrder() {
        return sourceImg.iterationOrder();
    }

    @Override
    public long min(int d) {
        return sourceImg.min(d);
    }

    @Override
    public long max(int d) {
        return sourceImg.max(d);
    }

    @Override
    public RandomAccess<T> randomAccess() {
        return sourceImg.randomAccess();
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        return sourceImg.randomAccess(interval);
    }

    @Override
    public int numDimensions() {
        return sourceImg.numDimensions();
    }
}
