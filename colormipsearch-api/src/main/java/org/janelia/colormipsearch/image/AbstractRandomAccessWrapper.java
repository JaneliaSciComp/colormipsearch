package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;

public abstract class AbstractRandomAccessWrapper<T> implements RandomAccess<T> {

    final RandomAccess<T> source;
    final long[] thisAccessPos;

    public AbstractRandomAccessWrapper(RandomAccess<T> source, int ndimensions) {
        this.source = source;
        thisAccessPos = new long[ndimensions];
    }

    AbstractRandomAccessWrapper(AbstractRandomAccessWrapper<T> c) {
        this.source = c.source.copy();
        thisAccessPos = c.thisAccessPos.clone();
    }

    @Override
    public int getIntPosition(final int d) {
        return (int) thisAccessPos[d];
    }

    @Override
    public long getLongPosition(final int d) {
        return thisAccessPos[d];
    }

    @Override
    public float getFloatPosition(final int d) {
        return (float) thisAccessPos[d];
    }

    @Override
    public double getDoublePosition(final int d) {
        return thisAccessPos[d];
    }

    @Override
    public int numDimensions() {
        return thisAccessPos.length;
    }

    void addAccessPos(final int[] distance) {
        for (int d = 0; d < numDimensions(); d++) {
            addAccessPos(distance[d], d);
        }
    }

    void addAccessPos(final long[] distance) {
        for (int d = 0; d < numDimensions(); d++) {
            addAccessPos(distance[d], d);
        }
    }

    void addAccessPos(final Localizable distance) {
        for (int d = 0; d < numDimensions(); d++) {
            addAccessPos(distance.getLongPosition(d), d);
        }
    }

    void addAccessPos(final long distance, final int d) {
        thisAccessPos[d] += distance;
    }

    void setAccessPos(final int[] location) {
        for (int d = 0; d < numDimensions(); d++) {
            setAccessPos(location[d], d);
        }
    }

    void setAccessPos(final long[] location) {
        for (int d = 0; d < numDimensions(); d++) {
            setAccessPos(location[d], d);
        }
    }

    void setAccessPos(final Localizable location) {
        for (int d = 0; d < numDimensions(); d++) {
            setAccessPos(location.getLongPosition(d), d);
        }
    }

    void setAccessPos(final long location, final int d) {
        thisAccessPos[d] = location;
    }

    @Override
    abstract public AbstractRandomAccessWrapper<T> copy();
}
