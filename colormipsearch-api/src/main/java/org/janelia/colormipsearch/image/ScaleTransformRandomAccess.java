package org.janelia.colormipsearch.image;

import java.util.Arrays;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.type.numeric.RealType;

public class ScaleTransformRandomAccess<T extends RealType<T>> extends AbstractRandomAccessWrapper<T> {

    private final BSplineCoefficientsInterpolator<T> interpolator;
    private final double[] scaleFactors;

    private final long[] thisAccessPos;
    private final long[] sourceAccessPos;
    private final double[] interpolatorAccessPos;

    public ScaleTransformRandomAccess(RandomAccess<T> source,
                                      BSplineCoefficientsInterpolator<T> interpolator,
                                      double[] scaleFactors) {
        super(source);
        this.scaleFactors = scaleFactors.clone();
        this.interpolator = interpolator;
        thisAccessPos = new long[source.numDimensions()];
        sourceAccessPos = new long[source.numDimensions()];
        interpolatorAccessPos = new double[source.numDimensions()];
    }

    private ScaleTransformRandomAccess(ScaleTransformRandomAccess<T> c) {
        super(c.source.copy());
        this.scaleFactors = c.scaleFactors.clone();
        this.interpolator = c.interpolator;
        this.thisAccessPos = c.thisAccessPos.clone();
        this.sourceAccessPos = c.sourceAccessPos.clone();
        this.interpolatorAccessPos = c.interpolatorAccessPos.clone();
    }

    @Override
    public T get() {
        for (int d = 0; d < scaleFactors.length; d++) {
            interpolatorAccessPos[d] = thisAccessPos[d] * scaleFactors[d];
            sourceAccessPos[d] = (long) interpolatorAccessPos[d];
        }
        T interpolated = interpolator.setPositionAndGet(interpolatorAccessPos);
        T p = source.setPositionAndGet(sourceAccessPos);
        p.setReal(interpolated.getRealDouble());
        return p;
    }

    @Override
    public void fwd(final int d) {
        ++thisAccessPos[d];
    }

    @Override
    public void bck(final int d) {
        --thisAccessPos[d];
    }

    @Override
    public void move(final int distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final long distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            move(localizable.getLongPosition(d), d);
        }
    }

    @Override
    public void move(final int[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }

    @Override
    public void move(final long[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }


    @Override
    public void setPosition(Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            thisAccessPos[d] = localizable.getLongPosition(d);
        }
    }

    @Override
    public void setPosition(int[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(long[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(int position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public ScaleTransformRandomAccess<T> copy() {
        return new ScaleTransformRandomAccess<>(this);
    }
}
