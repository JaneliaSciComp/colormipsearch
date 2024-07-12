package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;

public abstract class AbstractPositionableRandomAccessWrapper<T> extends AbstractRandomAccessWrapper<T> {

    public AbstractPositionableRandomAccessWrapper(RandomAccess<T> source, int ndimensions) {
        super(source, ndimensions);
    }

    AbstractPositionableRandomAccessWrapper(AbstractPositionableRandomAccessWrapper<T> c) {
        super(c);
    }

    @Override
    public void fwd(int d) {
        addAccessPos(1, d);
    }

    @Override
    public void bck(int d) {
        addAccessPos(-1, d);
    }

    @Override
    public void move(int distance, int d) {
        setAccessPos(getLongPosition(d) + distance, d);
    }

    @Override
    public void move(long distance, int d) {
        addAccessPos(distance, d);
    }

    @Override
    public void move(Localizable distance) {
        addAccessPos(distance);
    }

    @Override
    public void move(int[] distance) {
        addAccessPos(distance);
    }

    @Override
    public void move(long[] distance) {
        addAccessPos(distance);
    }

    @Override
    public void setPosition(Localizable location) {
        setAccessPos(location);
    }

    @Override
    public void setPosition(int[] location) {
        setAccessPos(location);
    }

    @Override
    public void setPosition(long[] location) {
        setAccessPos(location);
    }

    @Override
    public void setPosition(int location, int d) {
        setAccessPos(location, d);
    }

    @Override
    public void setPosition(long location, int d) {
        setAccessPos(location, d);
    }

    @Override
    abstract public AbstractPositionableRandomAccessWrapper<T> copy();
}
