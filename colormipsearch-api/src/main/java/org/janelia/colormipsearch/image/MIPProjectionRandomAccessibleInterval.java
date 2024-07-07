package org.janelia.colormipsearch.image;

import java.util.Comparator;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class MIPProjectionRandomAccessibleInterval<T extends Type<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

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
        super.dimensions(dimensions);
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
        for (int d = 0; d < min.length; d++) {
            if (d < axis) {
                min[d] = super.min(d);
            } else {
                min[d] = super.min(d+1);
            }
        }
    }

    @Override
    public void min(Positionable min) {
        for (int d = 0; d < min.numDimensions(); d++) {
            if (d < axis) {
                min.setPosition(super.min(d), d);
            } else {
                min.setPosition(super.min(d+1), d);
            }
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
        for (int d = 0; d < max.length; d++) {
            if (d < axis) {
                max[d] = super.max(d);
            } else {
                max[d] = super.max(d+1);
            }
        }
    }

    @Override
    public void max(Positionable max) {
        for (int d = 0; d < max.numDimensions(); d++) {
            if (d < axis) {
                max.setPosition(super.max(d), d);
            } else {
                max.setPosition(super.max(d+1), d);
            }
        }
    }

    @Override
    public int numDimensions() {
        return sourceInterval.numDimensions() - 1;
    }
}
