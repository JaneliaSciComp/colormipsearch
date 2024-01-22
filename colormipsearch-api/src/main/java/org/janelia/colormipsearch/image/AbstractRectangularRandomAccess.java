package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;

public abstract class AbstractRectangularRandomAccess<T> implements RandomAccess<T> {

    final RectIntervalHelper rectIntervalHelper;
    final long[] tmpPos;

    public AbstractRectangularRandomAccess(long[] shape) {
        this(new RectIntervalHelper(shape));
    }

    public AbstractRectangularRandomAccess(long[] min, long[] max, long[] shape) {
        this(new RectIntervalHelper(min, max, shape));
    }

    AbstractRectangularRandomAccess(RectIntervalHelper rectIntervalHelper) {
        this.rectIntervalHelper = rectIntervalHelper;
        this.tmpPos = new long[rectIntervalHelper.numDimensions()];
    }

    @Override
    public void fwd(int d) {
        rectIntervalHelper.moveAxisPos(d, 1);
    }

    @Override
    public void bck(int d) {
        rectIntervalHelper.moveAxisPos(d, -1);
    }

    @Override
    public void move(int distance, int d) {
        rectIntervalHelper.moveAxisPos(d, distance);
    }

    @Override
    public void move(long distance, int d) {
        rectIntervalHelper.moveAxisPos(d, distance);
    }

    @Override
    public void move(Localizable distance) {
        rectIntervalHelper.movePos(distance);
    }

    @Override
    public void move(int[] distance) {
        rectIntervalHelper.movePos(distance);
    }

    @Override
    public void move(long[] distance) {
        rectIntervalHelper.movePos(distance);
    }

    @Override
    public void setPosition(Localizable position) {
        rectIntervalHelper.setPosition(position);
    }

    @Override
    public void setPosition(int[] position) {
        rectIntervalHelper.setPosition(position);
    }

    @Override
    public void setPosition(long[] position) {
        rectIntervalHelper.setPosition(position);
    }

    @Override
    public void setPosition(int position, int d) {
        rectIntervalHelper.setAxisPos(d, position);
    }

    @Override
    public void setPosition(long position, int d) {
        rectIntervalHelper.setAxisPos(d, position);
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
