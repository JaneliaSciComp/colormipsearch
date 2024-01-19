package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class GeomTransformAccess<T> extends AbstractIterablePositionableAccess<T> {

    private final RandomAccess<T> source;
    private final GeomTransform geomTransform;

    public GeomTransformAccess(RandomAccess<T> source, Interval interval, GeomTransform geomTransform) {
        this(source, new RectCoordsHelper(interval), geomTransform);
    }

    private GeomTransformAccess(RandomAccess<T> source, RectCoordsHelper coordsHelper, GeomTransform geomTransform) {
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
    public GeomTransformAccess<T> copy() {
        return new GeomTransformAccess<>(source.copy(), coordsHelper.copy(), geomTransform);
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
