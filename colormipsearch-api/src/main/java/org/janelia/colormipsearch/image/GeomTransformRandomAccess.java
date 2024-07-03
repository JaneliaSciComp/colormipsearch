package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;

public class GeomTransformRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    private final GeomTransform geomTransform;

    private final long[] thisAccessPos;
    private final long[] wrappedAccessPos;

    public GeomTransformRandomAccess(RandomAccess<T> source, GeomTransform geomTransform) {
        super(source);
        this.geomTransform = geomTransform;
        thisAccessPos = new long[geomTransform.getSourceDims()];
        wrappedAccessPos = new long[geomTransform.getTargetDims()];
    }

    private GeomTransformRandomAccess(GeomTransformRandomAccess<T> c) {
        super(c.source.copy());
        this.geomTransform = c.geomTransform;
        thisAccessPos = new long[geomTransform.getSourceDims()];
        wrappedAccessPos = new long[geomTransform.getTargetDims()];
    }

    @Override
    public T get() {
        geomTransform.apply(thisAccessPos, wrappedAccessPos);
        T p = source.setPositionAndGet(wrappedAccessPos);
        return p;
    }

    @Override
    public void fwd(final int d) {
        ++thisAccessPos[d];
    }

    @Override
    public void bck(final int d) {
        --thisAccessPos[d];
    }

    @Override
    public void move(final int distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final long distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            move(localizable.getLongPosition(d), d);
        }
    }

    @Override
    public void move(final int[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }

    @Override
    public void move(final long[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }


    @Override
    public void setPosition(Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            thisAccessPos[d] = localizable.getLongPosition(d);
        }
    }

    @Override
    public void setPosition(int[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(long[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(int position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public GeomTransformRandomAccess<T> copy() {
        return new GeomTransformRandomAccess<>(this);
    }

    @Override
    public int numDimensions() {
        return geomTransform.getSourceDims();
    }
}
