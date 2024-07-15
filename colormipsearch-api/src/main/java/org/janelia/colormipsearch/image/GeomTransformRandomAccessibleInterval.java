package org.janelia.colormipsearch.image;

import java.util.function.Supplier;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

/**
 * @param <T> pixel type
 */
public class GeomTransformRandomAccessibleInterval<T> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final GeomTransform geomTransform;

    GeomTransformRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                          GeomTransform geomTransform) {
        super(source);
        this.geomTransform = geomTransform;
    }

    @Override
    public GeomTransformRandomAccess<T> randomAccess() {
        return new GeomTransformRandomAccess<>(
                sourceInterval.randomAccess(),
                geomTransform
        );
    }

    @Override
    public GeomTransformRandomAccess<T>  randomAccess(Interval interval) {
        return new GeomTransformRandomAccess<>(
                sourceInterval.randomAccess(interval),
                geomTransform
        );
    }

}
