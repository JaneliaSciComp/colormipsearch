package org.janelia.colormipsearch.image;

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
        localize(thisAccessPos);
        geomTransform.apply(thisAccessPos, wrappedAccessPos);
        return source.setPositionAndGet(wrappedAccessPos);
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
