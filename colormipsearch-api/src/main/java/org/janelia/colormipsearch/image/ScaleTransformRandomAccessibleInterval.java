package org.janelia.colormipsearch.image;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;

public class ScaleTransformRandomAccessibleInterval<T extends IntegerType<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final long scaledAxisDimension;
    private final int axis; // axis on which to values get interpolated

    ScaleTransformRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                            long scaledAxisDimension,
                                            int axis) {
        super(source);
        this.scaledAxisDimension = scaledAxisDimension;
        this.axis = axis;
    }

    @Override
    public void dimensions(long[] dimensions) {
        for (int d = 0; d < dimensions.length; d++) {
            dimensions[d] = dimension(d);
        }
    }

    @Override
    public long dimension(int d) {
        if (d == axis) {
            return scaledAxisDimension;
        } else {
            return sourceInterval.dimension(d);
        }
    }

    @Override
    public long min(int d) {
        if (d == axis) {
            return getScaledAxisValue(sourceInterval.min(d));
        } else {
            return sourceInterval.min(d);
        }
    }

    @Override
    public void min(long[] min) {
        for (int d = 0; d < min.length; d++) {
            min[d] = min(d);
        }
    }

    @Override
    public void min(Positionable min) {
        for (int d = 0; d < numDimensions(); d++) {
            min.setPosition(min(d), d);
        }
    }

    @Override
    public long max(int d) {
        if (d == axis) {
            return min(d) + dimension(d) - 1;
        } else {
            return sourceInterval.max(d);
        }
    }

    @Override
    public void max(long[] max) {
        for (int d = 0; d < numDimensions(); d++) {
            max[d] = max(d);
        }
    }

    @Override
    public void max(Positionable max) {
        for (int d = 0; d < numDimensions(); d++) {
            max.setPosition(max(d), d);
        }
    }

    @Override
    public ScaleTransformRandomAccess<T> randomAccess() {
        return new ScaleTransformRandomAccess<>(
                sourceInterval.randomAccess(),
                sourceInterval,
                axis,
                scaledAxisDimension,
                sourceInterval.dimension(axis)
        );
    }

    @Override
    public ScaleTransformRandomAccess<T>  randomAccess(Interval interval) {
        return new ScaleTransformRandomAccess<>(
                sourceInterval.randomAccess(interval),
                sourceInterval,
                axis,
                scaledAxisDimension,
                sourceInterval.dimension(axis)
        );
    }

    private long getScaledAxisValue(long currentValue) {
        return (long) Math.floor((double)currentValue * (scaledAxisDimension / sourceInterval.dimension(axis)));
    }

}
