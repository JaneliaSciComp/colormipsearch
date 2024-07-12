package org.janelia.colormipsearch.image;

import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;

public class ScaleTransformRandomAccess<T extends RealType<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    private final static double ALPHA = 0.5;

    private final int axis;
    private final double scaleToSourceFactor;
    private final long[] sourceAccessPos;
    private final double[] sourceRealAccessPos;
    private final T pxType;

    public ScaleTransformRandomAccess(RandomAccess<T> source,
                                      int axis,
                                      double scaleToSourceFactor) {
        super(source, source.numDimensions());
        this.axis = axis;
        this.scaleToSourceFactor = scaleToSourceFactor;
        this.sourceAccessPos = new long[source.numDimensions()];
        this.sourceRealAccessPos = new double[source.numDimensions()];
        this.pxType = source.get().createVariable();
    }

    private ScaleTransformRandomAccess(ScaleTransformRandomAccess<T> c) {
        super(c);
        this.axis = c.axis;
        this.scaleToSourceFactor = c.scaleToSourceFactor;
        this.sourceAccessPos = c.sourceAccessPos.clone();
        this.sourceRealAccessPos = c.sourceRealAccessPos.clone();
        this.pxType = c.pxType.createVariable();
    }

    @Override
    public T get() {
        computeSourcePos();
        long t0 = sourceAccessPos[axis];
        sourceAccessPos[axis] = t0 - 1;
        source.setPosition(sourceAccessPos);
        double interpolatedValue = 0;
        for (int i = 0; i <= 3; i++) {
            long t = t0 - 1 + i;
            interpolatedValue = interpolatedValue + source.get().getRealDouble() * cubic(sourceRealAccessPos[axis] - t);
        }
        pxType.setReal(interpolatedValue);
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
