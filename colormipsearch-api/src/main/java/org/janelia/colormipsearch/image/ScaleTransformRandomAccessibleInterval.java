package org.janelia.colormipsearch.image;

import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.interpolation.randomaccess.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class ScaleTransformRandomAccessibleInterval<T extends RealType<T>> extends AbstractWrappedInterval<RandomAccessibleInterval<T>> implements RandomAccessibleInterval<T> {

    private BSplineCoefficientsInterpolatorFactory<T, T> interpolatorFactory;
    private double[] scaleFactors;
    private double[] inverseScaleFactors;

    ScaleTransformRandomAccessibleInterval(RandomAccessibleInterval<T> source,
                                            double[] scaleFactors) {
        super(source);
        this.scaleFactors = scaleFactors.clone();
        this.interpolatorFactory = new BSplineCoefficientsInterpolatorFactory<>(source);
        this.inverseScaleFactors = new double[scaleFactors.length];
        for (int d = 0; d < scaleFactors.length; d++) {
            inverseScaleFactors[d] = 1 / scaleFactors[d];
        }
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
        return scaleValue(sourceInterval.max(d), d);
    }

    @Override
    public void max(long[] max) {
        scaleValues(sourceInterval.maxAsLongArray(), max);
    }

    @Override
    public void max(Positionable max) {
        for (int d = 0; d < numDimensions(); d++) {
            max.setPosition(scaleValue(sourceInterval.max(d), d), d);
        }
    }

    @Override
    public ScaleTransformRandomAccess<T> randomAccess() {
        return new ScaleTransformRandomAccess<>(
                sourceInterval.randomAccess(),
                interpolatorFactory.create(sourceInterval),
                inverseScaleFactors
        );
    }

    @Override
    public ScaleTransformRandomAccess<T>  randomAccess(Interval interval) {
        return new ScaleTransformRandomAccess<>(
                sourceInterval.randomAccess(interval),
                interpolatorFactory.create(sourceInterval),
                inverseScaleFactors
        );
    }

    private void scaleValues(long[] currentValues, long[] newValues) {
        for (int d = 0; d < currentValues.length; d++) {
            newValues[d] = scaleValue(currentValues[d], d);
        }
    }

    private long scaleValue(long currentValue, int d) {
        return (long)(currentValue * scaleFactors[d]);
    }

}
