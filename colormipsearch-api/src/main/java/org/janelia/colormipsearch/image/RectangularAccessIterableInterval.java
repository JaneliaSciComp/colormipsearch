package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import net.imglib2.view.TransformBuilder;

public class RectangularAccessIterableInterval<T> implements RandomAccessibleInterval<T>, IterableInterval<T> {

    private final RandomAccess<T> sourceAccess;
    private final RectIntervalHelper rectIntervalHelper;

    public RectangularAccessIterableInterval(RandomAccess<T> sourceAccess, Interval interval) {
        this(sourceAccess, new RectIntervalHelper(interval));
    }

    public RectangularAccessIterableInterval(RandomAccessibleInterval<T> sourceAccess) {
        this(sourceAccess.randomAccess(), new RectIntervalHelper(sourceAccess));
    }

    private RectangularAccessIterableInterval(RandomAccess<T> sourceAccess, RectIntervalHelper rectIntervalHelper) {
        this.sourceAccess = sourceAccess;
        this.rectIntervalHelper = rectIntervalHelper;
    }

    @Override
    public RandomAccess<T> randomAccess() {
        return sourceAccess;
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        return TransformBuilder.getEfficientRandomAccessible(interval, this).randomAccess();
    }

    @Override
    public Cursor<T> cursor() {
        return new RandomAccessibleIntervalCursor<>(this);
//        return new RectangularCursor<>(sourceAccess, rectIntervalHelper);
    }

    @Override
    public Cursor<T> localizingCursor() {
        return new RandomAccessibleIntervalCursor<>(this);
//        return new RectangularCursor<>(sourceAccess, rectIntervalHelper);
    }

    @Override
    public long min(int d) {
        return rectIntervalHelper.minAxis(d);
    }

    @Override
    public long max(int d) {
        return rectIntervalHelper.maxAxis(d);
    }

    @Override
    public int numDimensions() {
        return rectIntervalHelper.numDimensions();
    }

    @Override
    public long size() {
        return rectIntervalHelper.getSize();
    }

    @Override
    public Object iterationOrder() {
        return rectIntervalHelper;
    }

}
