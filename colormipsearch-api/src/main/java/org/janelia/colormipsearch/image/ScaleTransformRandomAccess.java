package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.type.numeric.RealType;

public class ScaleTransformRandomAccess<T extends RealType<T>> extends AbstractRandomAccessWrapper<T> {

    private final double[] scaleFactors;
    private final long[] thisAccessPos;
    private final long[] sourceAccessPos;
    private final double[] sourceRealAccessPos;
    private final List<long[]> neighbors;
    private final T pxType;

    public ScaleTransformRandomAccess(RandomAccess<T> source,
                                      double[] scaleFactors) {
        super(source);
        this.scaleFactors = scaleFactors.clone();
        this.thisAccessPos = new long[source.numDimensions()];
        this.sourceAccessPos = new long[source.numDimensions()];
        this.sourceRealAccessPos = new double[source.numDimensions()];
        this.neighbors = ImageAccessUtils.streamNeighborsWithinDist(source.numDimensions(), 1, true)
                .filter(coords -> Arrays.stream(coords).allMatch(p -> p >= 0))
                .collect(Collectors.toList());
        this.pxType = source.get().createVariable();
    }

    private ScaleTransformRandomAccess(ScaleTransformRandomAccess<T> c) {
        super(c.source.copy());
        this.scaleFactors = c.scaleFactors.clone();
        this.thisAccessPos = c.thisAccessPos.clone();
        this.sourceAccessPos = c.sourceAccessPos.clone();
        this.sourceRealAccessPos = c.sourceRealAccessPos.clone();
        this.neighbors = c.neighbors;
        this.pxType = c.pxType.createVariable();
    }

    @Override
    public int numDimensions() {
        return scaleFactors.length;
    }

    @Override
    public T get() {
        for (int d = 0; d < scaleFactors.length; d++) {
            sourceRealAccessPos[d] = thisAccessPos[d] * scaleFactors[d];
            sourceAccessPos[d] = (long)Math.floor(sourceRealAccessPos[d]);
        }
        double[] sourceValues = new double[1 << numDimensions()];
        long[] neighborCoords = new long[numDimensions()];
        for (long[] neighbor : neighbors) {
            CoordUtils.addCoords(sourceAccessPos, neighbor, 1, neighborCoords);
            double v = source.setPositionAndGet(neighborCoords).getRealDouble();
            sourceValues[asBase2Number(neighbor)] = v;
        }
        double interpolatedValue = computeLinearInterpolation(sourceValues);
        pxType.setReal(interpolatedValue);
        return pxType;
    }

    private double computeLinearInterpolation(double[] sourceValues) {
        double[] interpolatedValues = sourceValues;
        for (int d = 0; d < numDimensions(); d++) {
            int newValuesBits = numDimensions() - d - 1;
            double[] newValues = new double[1 << newValuesBits];
            double delta = sourceRealAccessPos[d] - sourceAccessPos[d];
            // do linear interpolation in each direction
            for (int vi = 0; vi < interpolatedValues.length; vi++) {
                if ((vi & 1) != 0) {
                    int prevValueIndex = vi - 1;
                    int newValueIndex = vi >> 1;
                    double prevValue = interpolatedValues[prevValueIndex];
                    double currValue = interpolatedValues[vi];
                    newValues[newValueIndex] = prevValue * (1 - delta) + currValue * delta;
                }
            }
            interpolatedValues = newValues;
        }
        return interpolatedValues[0];
    }

    private int asBase2Number(long[] coord) {
        int n = 0;
        for(int d = 0; d < coord.length; d++) {
            if (coord[d] == 1) {
                n += (1 << d);
            } else if (coord[d] != 0) {
                throw new IllegalArgumentException("Operation is not supported for: " + Arrays.toString(coord));
            }
        }
        return n;
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
    public long getLongPosition(final int d) {
        return thisAccessPos[d];
    }

    @Override
    public void localize(final long[] position) {
        System.arraycopy(thisAccessPos, 0, position, 0, thisAccessPos.length);
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
