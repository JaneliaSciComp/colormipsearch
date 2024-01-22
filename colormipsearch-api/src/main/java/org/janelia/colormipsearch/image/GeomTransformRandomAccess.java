package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;

public class GeomTransformRandomAccess<T> extends AbstractRectangularRandomAccess<T> {

    private final RandomAccess<T> source;
    private final GeomTransform geomTransform;

    public GeomTransformRandomAccess(RandomAccess<T> source, Interval interval, GeomTransform geomTransform) {
        this(source, new RectIntervalHelper(interval), geomTransform);
    }

    private GeomTransformRandomAccess(RandomAccess<T> source, RectIntervalHelper coordsHelper, GeomTransform geomTransform) {
        super(coordsHelper);
        this.source = source;
        this.geomTransform = geomTransform;
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

    @Override
    public void localize(int[] position) {
        super.localize(tmpPos);
        long[] transformedCurrentPos =  geomTransform.apply(tmpPos);
        for (int d = 0; d < position.length; d++)
            position[d] = (int) transformedCurrentPos[d];
    }

    @Override
    public void localize(long[] position) {
        super.localize(tmpPos);
        long[] transformedCurrentPos =  geomTransform.apply(tmpPos);
        for (int d = 0; d < position.length; d++)
            position[d] = transformedCurrentPos[d];
    }

    @Override
    public long getLongPosition(int d) {
        super.localize(tmpPos);
        long[] transformedCurrentPos =  geomTransform.apply(tmpPos);
        return transformedCurrentPos[d];
    }
}
