package org.janelia.colormipsearch.image;

import java.util.Comparator;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class MIPProjectionRandomAccessibleInterval<T extends Type<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>>
                                                                      implements RandomAccessibleInterval<T> {

    private final Comparator<T> pixelComparator;
    private final int axis;
    private final long minAxis;
    private final long maxAxis;

    MIPProjectionRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                          Comparator<T> pixelComparator,
                                          int axis,
                                          long minAxis, long maxAxis) {
        super(source);
        this.pixelComparator = pixelComparator;
        this.axis = axis;
        this.minAxis = minAxis;
        this.maxAxis = maxAxis;
    }

    @Override
    public MIPProjectionRandomAccess<T> randomAccess() {
        return new MIPProjectionRandomAccess<>(
                sourceInterval.randomAccess(),
                pixelComparator,
                axis,
                minAxis, maxAxis);
    }

    @Override
    public MIPProjectionRandomAccess<T>  randomAccess(Interval interval) {
        return new MIPProjectionRandomAccess<>(
                sourceInterval.randomAccess(interval),
                pixelComparator,
                axis,
                minAxis, maxAxis);
    }

    @Override
    public long dimension(int d) {
        if (d < axis) {
            return super.dimension(d);
        } else {
            return super.dimension(d + 1);
        }
    }

    @Override
    public void dimensions(long[] dimensions) {
        for (int d = 0; d < numDimensions(); d++) {
            dimensions[d] = dimension(d);
        }
    }


    @Override
    public long min(int d) {
        if (d < axis) {
            return super.min(d);
        } else {
            return super.min(d + 1);
        }
    }

    @Override
    public void min(long[] min) {
        for (int d = 0; d < numDimensions(); d++) {
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
        if (d < axis) {
            return super.max(d);
        } else {
            return super.max(d + 1);
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
    public int numDimensions() {
        return sourceInterval.numDimensions() - 1;
    }
}
