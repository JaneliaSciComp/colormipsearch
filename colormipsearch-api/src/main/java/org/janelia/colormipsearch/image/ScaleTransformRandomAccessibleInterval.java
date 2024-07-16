package org.janelia.colormipsearch.image;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;

public class ScaleTransformRandomAccessibleInterval<T extends IntegerType<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private final double axisScaleFactor;
    private final int axis; // axis on which to values get interpolated

    ScaleTransformRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                            double scaleFactor,
                                            int axis) {
        super(source);
        this.axisScaleFactor = scaleFactor;
        this.axis = axis;
    }

    @Override
    public void dimensions(long[] dimensions) {
        scaleValues(sourceInterval.dimensionsAsLongArray(), dimensions);
    }

    @Override
    public long dimension(int d) {
        return scaleValue(sourceInterval.dimension(d), d);
    }

    @Override
    public long min(int d) {
        return scaleValue(sourceInterval.min(d), d);
    }

    @Override
    public void min(long[] min) {
        scaleValues(sourceInterval.minAsLongArray(), min);
    }

    @Override
    public void min(Positionable min) {
        for (int d = 0; d < numDimensions(); d++) {
            min.setPosition(scaleValue(sourceInterval.min(d), d), d);
        }
    }

    @Override
    public long max(int d) {
        return scaleValue(sourceInterval.min(d), d) + scaleValue(sourceInterval.dimension(d), d) - 1;
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
                1. /axisScaleFactor
        );
    }

    @Override
    public ScaleTransformRandomAccess<T>  randomAccess(Interval interval) {
        return new ScaleTransformRandomAccess<>(
                sourceInterval.randomAccess(interval),
                sourceInterval,
                axis,
                1. / axisScaleFactor
        );
    }

    private void scaleValues(long[] currentValues, long[] newValues) {
        for (int d = 0; d < currentValues.length; d++) {
            if (d == axis) {
                newValues[d] = (long) Math.floor(currentValues[d] * axisScaleFactor);
            } else {
                newValues[d] = currentValues[d];
            }
        }
    }

    private long scaleValue(long currentValue, int d) {
        if (d == axis) {
            return (long) Math.floor(currentValue * axisScaleFactor);
        } else {
            return currentValue;
        }
    }

}
