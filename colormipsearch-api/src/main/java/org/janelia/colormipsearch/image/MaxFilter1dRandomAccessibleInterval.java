package org.janelia.colormipsearch.image;

import java.util.Comparator;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;

/**
 * @param <T> pixel type
 */
public class MaxFilter1dRandomAccessibleInterval<T extends Type<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final Max<T> maxOf;
    private final int radius;
    private final int axis;

    MaxFilter1dRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                        Max<T> maxOf,
                                        int radius,
                                        int axis) {
        super(source);
        this.maxOf = maxOf;
        this.radius = radius;
        this.axis = axis;
    }

    @Override
    public MaxFilter1dRandomAccess<T> randomAccess() {
        return new MaxFilter1dRandomAccess<>(
                sourceInterval.randomAccess(),
                sourceInterval,
                maxOf,
                radius,
                axis
        );
    }

    @Override
    public MaxFilter1dRandomAccess<T>  randomAccess(Interval interval) {
        return new MaxFilter1dRandomAccess<>(
                sourceInterval.randomAccess(interval),
                interval,
                maxOf,
                radius,
                axis
        );
    }

}
