package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.IntegerType;

public class ScaleTransformRandomAccess<T extends IntegerType<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    private final static double ALPHA = 0.5;

    private final Interval sourceDataInterval;
    private final int axis;
    private final long thisAxisDimension;
    private final long sourceAxisDimension;
    private final double scaleToSourceFactor;
    private final long[] sourceAccessPos;
    private final double[] sourceRealAccessPos;
    private final T pxType;
    private final int pxMaxVal;

    public ScaleTransformRandomAccess(RandomAccess<T> source,
                                      Interval sourceDataInterval,
                                      int axis,
                                      long thisAxisDimension,
                                      long sourceAxisDimension) {
        super(source, source.numDimensions());
        this.sourceDataInterval = sourceDataInterval;
        this.axis = axis;
        this.thisAxisDimension = thisAxisDimension;
        this.sourceAxisDimension = sourceAxisDimension;
        this.sourceAccessPos = new long[source.numDimensions()];
        this.sourceRealAccessPos = new double[source.numDimensions()];
        this.pxType = source.get().createVariable();
        this.pxMaxVal = (int) pxType.getMaxValue();
        this.scaleToSourceFactor = (double) sourceAxisDimension / thisAxisDimension;
    }

    private ScaleTransformRandomAccess(ScaleTransformRandomAccess<T> c) {
        super(c);
        this.sourceDataInterval = c.sourceDataInterval;
        this.axis = c.axis;
        this.thisAxisDimension = c.thisAxisDimension;
        this.sourceAxisDimension = c.sourceAxisDimension;
        this.sourceAccessPos = c.sourceAccessPos.clone();
        this.sourceRealAccessPos = c.sourceRealAccessPos.clone();
        this.pxType = c.pxType.createVariable();
        this.pxMaxVal = c.pxMaxVal;
        this.scaleToSourceFactor = c.scaleToSourceFactor;
    }

    @Override
    public T get() {
        computeSourcePos();
        long t0 = sourceAccessPos[axis];
        sourceAccessPos[axis] = t0 - 1;
        source.setPosition(sourceAccessPos);
        double interpolatedValue = 0;
        // because of the interpolation for multichannel images
        // the scale must be applied for each channel
        for (int i = 0; i <= 3; i++) {
            long t = t0 - 1 + i;
            double v;
            if (CoordUtils.contains(sourceDataInterval, t, axis)) {
                v = source.get().getRealDouble();
            } else {
                v = 0;
            }
            interpolatedValue += v * cubic(sourceRealAccessPos[axis] - t);
            source.fwd(axis);
        }
        int pxValue = (int)(interpolatedValue + 0.5);
        if (pxValue < 0) {
            pxType.setInteger(0);
        } else if (interpolatedValue > pxMaxVal) {
            pxType.setInteger(pxMaxVal);
        } else {
            pxType.setInteger(pxValue);
        }
        return pxType;
    }

    private void computeSourcePos() {
        for (int d = 0; d < numDimensions(); d++) {
            if (d ==  axis) {
                sourceRealAccessPos[d] = thisAccessPos[d] * scaleToSourceFactor;
            } else {
                sourceRealAccessPos[d] = thisAccessPos[d];
            }
            sourceAccessPos[d] = (long)Math.floor(sourceRealAccessPos[d]);
        }
    }

    private double cubic(double x) {
        if (x < 0.0) x = -x;
        double z = 0.0;
        if (x < 1.0)
            z = x*x*(x*(-ALPHA +2.0) + (ALPHA -3.0)) + 1.0;
        else if (x < 2.0)
            z = -ALPHA*x*x*x + 5.0*ALPHA*x*x - 8.0*ALPHA*x + 4.0*ALPHA;
        return z;
    }

    @Override
    public ScaleTransformRandomAccess<T> copy() {
        return new ScaleTransformRandomAccess<>(this);
    }
}
