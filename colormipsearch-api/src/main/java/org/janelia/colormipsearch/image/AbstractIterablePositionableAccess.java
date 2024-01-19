package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;

public abstract class AbstractIterablePositionableAccess<T> implements IterableRandomAccess<T> {

    final RectCoordsHelper coordsHelper;
    final long[] tmpPos;

    public AbstractIterablePositionableAccess(long[] shape) {
        this(new RectCoordsHelper(shape));
    }

    public AbstractIterablePositionableAccess(long[] min, long[] max, long[] shape) {
        this(new RectCoordsHelper(min, max, shape));
    }

    AbstractIterablePositionableAccess(RectCoordsHelper coordsHelper) {
        this.coordsHelper = coordsHelper;
        this.tmpPos = new long[coordsHelper.numDimensions()];
    }

    @Override
    public void fwd(int d) {
        coordsHelper.moveAxisPos(d, 1);
    }

    @Override
    public void bck(int d) {
        coordsHelper.moveAxisPos(d, -1);
    }

    @Override
    public void move(int distance, int d) {
        coordsHelper.moveAxisPos(d, distance);
    }

    @Override
    public void move(long distance, int d) {
        coordsHelper.moveAxisPos(d, distance);
    }

    @Override
    public void move(Localizable distance) {
        coordsHelper.movePos(distance);
    }

    @Override
    public void move(int[] distance) {
        coordsHelper.movePos(distance);
    }

    @Override
    public void move(long[] distance) {
        coordsHelper.movePos(distance);
    }

    @Override
    public void setPosition(Localizable position) {
        coordsHelper.setPosition(position);
    }

    @Override
    public void setPosition(int[] position) {
        coordsHelper.setPosition(position);
    }

    @Override
    public void setPosition(long[] position) {
        coordsHelper.setPosition(position);
    }

    @Override
    public void setPosition(int position, int d) {
        coordsHelper.setAxisPos(d, position);
    }

    @Override
    public void setPosition(long position, int d) {
        coordsHelper.setAxisPos(d, position);
    }

    @Override
    public int numDimensions() {
        return coordsHelper.numDimensions();
    }

    @Override
    public void fwd() {
        coordsHelper.nextPos();
    }

    @Override
    public void reset() {
        coordsHelper.resetPos();
    }

    @Override
    public boolean hasNext() {
        return coordsHelper.hasNextPos();
    }

    @Override
    public void localize(long[] position) {
        coordsHelper.currentPos(position);
    }

    @Override
    public long getLongPosition(int d) {
        return coordsHelper.currentAxisPos(d);
    }

    @Override
    public long getSize() {
        return coordsHelper.getSize();
    }
}
