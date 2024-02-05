package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;

public abstract class AbstractRectangularCursor<T> implements Cursor<T> {

    final RectIntervalHelper rectIntervalHelper;

    public AbstractRectangularCursor(long[] shape) {
        this(new RectIntervalHelper(shape));
    }

    public AbstractRectangularCursor(Interval interval) {
        this(new RectIntervalHelper(interval));
    }

    public AbstractRectangularCursor(long[] min, long[] max, long[] shape) {
        this(new RectIntervalHelper(min, max, shape));
        rectIntervalHelper.resetPos();
    }

    AbstractRectangularCursor(RectIntervalHelper rectIntervalHelper) {
        this.rectIntervalHelper = rectIntervalHelper;
    }

    @Override
    public void fwd() {
        rectIntervalHelper.nextPos();
    }

    @Override
    public void reset() {
        rectIntervalHelper.resetPos();
    }

    @Override
    public boolean hasNext() {
        return rectIntervalHelper.hasNextPos();
    }

    @Override
    public int numDimensions() {
        return rectIntervalHelper.numDimensions();
    }

    @Override
    public void localize(long[] position) {
        rectIntervalHelper.currentPos(position);
    }

    @Override
    public long getLongPosition(int d) {
        return rectIntervalHelper.currentAxisPos(d);
    }

}
