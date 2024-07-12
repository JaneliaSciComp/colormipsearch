package org.janelia.colormipsearch.image;

import java.util.Arrays;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;

public class GeomTransformRandomAccess<T> extends AbstractPositionableRandomAccessWrapper<T> {

    private final GeomTransform geomTransform;

    private final long[] sourceAccessPos;

    public GeomTransformRandomAccess(RandomAccess<T> source, GeomTransform geomTransform) {
        super(source, source.numDimensions());
        this.geomTransform = geomTransform;
        sourceAccessPos = new long[source.numDimensions()];
    }

    private GeomTransformRandomAccess(GeomTransformRandomAccess<T> c) {
        super(c);
        this.geomTransform = c.geomTransform;
        this.sourceAccessPos = Arrays.copyOf(c.sourceAccessPos, c.sourceAccessPos.length);
    }

    @Override
    public T get() {
        geomTransform.apply(thisAccessPos, sourceAccessPos);
        T p = source.setPositionAndGet(sourceAccessPos);
        return p;
    }

    @Override
    public GeomTransformRandomAccess<T> copy() {
        return new GeomTransformRandomAccess<>(this);
    }
}
