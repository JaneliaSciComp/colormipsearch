package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class GeomTransformRandomAccess<T> extends AbstractRectangularRandomAccess<T> {

    private final RandomAccess<T> source;
    private final GeomTransform geomTransform;
    private final long[] tmpPos;

    public GeomTransformRandomAccess(RandomAccess<T> source, Interval interval, GeomTransform geomTransform) {
        this(source, new RectIntervalHelper(interval), geomTransform);
    }

    private GeomTransformRandomAccess(RandomAccess<T> source, RectIntervalHelper coordsHelper, GeomTransform geomTransform) {
        super(coordsHelper);
        this.source = source;
        this.geomTransform = geomTransform;
        this.tmpPos = new long[source.numDimensions()];
    }

    @Override
    public T get() {
        super.localize(tmpPos);
        return source.setPositionAndGet(geomTransform.apply(tmpPos));
    }

    @Override
    public GeomTransformRandomAccess<T> copy() {
        return new GeomTransformRandomAccess<>(source.copy(), rectIntervalHelper.copy(), geomTransform);
    }
}
