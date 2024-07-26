package org.janelia.colormipsearch.image;

import java.util.Arrays;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.Type;

public class GeomTransformRandomAccess<T extends Type<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    private final Interval sourceDataInterval;
    private final GeomTransform geomTransform;
    private final long[] sourceAccessPos;
    private final T pxType;

    public GeomTransformRandomAccess(RandomAccess<T> source,
                                     Interval sourceDataInterval,
                                     GeomTransform geomTransform) {
        super(source, source.numDimensions());
        this.sourceDataInterval = sourceDataInterval;
        this.geomTransform = geomTransform;
        sourceAccessPos = new long[source.numDimensions()];
        pxType = source.get().createVariable();
    }

    private GeomTransformRandomAccess(GeomTransformRandomAccess<T> c) {
        super(c);
        this.sourceDataInterval = c.sourceDataInterval;
        this.geomTransform = c.geomTransform;
        this.sourceAccessPos = Arrays.copyOf(c.sourceAccessPos, c.sourceAccessPos.length);
        this.pxType = c.pxType.createVariable();
    }

    @Override
    public T get() {
        geomTransform.apply(thisAccessPos, sourceAccessPos);
        if (CoordUtils.contains(sourceDataInterval, sourceAccessPos)) {
            return source.setPositionAndGet(sourceAccessPos);
        } else {
            return pxType;
        }
    }

    @Override
    public GeomTransformRandomAccess<T> copy() {
        return new GeomTransformRandomAccess<>(this);
    }
}
