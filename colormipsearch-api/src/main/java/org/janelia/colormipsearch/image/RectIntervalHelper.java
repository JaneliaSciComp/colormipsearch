package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class RectIntervalHelper {

    private final long[] shape;
    private final long[] min;
    private final long[] max;
    private final long[] currentPos;
    private final long[] stride;
    private int currentDim;

    public RectIntervalHelper(long[] shape) {
        this(ImageAccessUtils.getZero(shape.length), ImageAccessUtils.getMax(shape), shape);
    }

    public RectIntervalHelper(Interval interval) {
        this(interval.minAsLongArray(), interval.maxAsLongArray(), interval.dimensionsAsLongArray());
    }

    public RectIntervalHelper(long[] min, long[] max, long[] shape) {
        this(min, max, shape, min, 0);
    }

    private RectIntervalHelper(long[] min,
                               long[] max,
                               long[] shape,
                               long[] currentPos,
                               int currentDim) {
        assert currentPos.length > 0;
        assert currentPos.length == min.length;
        assert currentPos.length == max.length;
        assert currentPos.length == shape.length;
        assert currentDim < currentPos.length;

        this.min = min.clone();
        this.max = max.clone();
        this.shape = shape.clone();
        this.currentPos = currentPos.clone();
        this.currentDim = currentDim;
        this.stride = new long[shape.length];
        setStrides();
    }

    private void setStrides() {
        long strideAccumulator = 1;
        for (int d = 0; d < stride.length; d++) {
            stride[d] = strideAccumulator;
            if (shape[d] == 0) {
                throw new IllegalArgumentException("Invalid shape axis dimension: dimension for " + d + " axis is 0");
            }
            strideAccumulator *= shape[d];
        }

    }

    public RectIntervalHelper copy() {
        return new RectIntervalHelper(min, max, shape, currentPos, currentDim);
    }

    public long[] linearIndexToRectCoords(long index) {
        long[] coords = new long[shape.length];
        unsafeLinearIndexToRectCoords(index, coords);
        return coords;
    }

    public boolean contains(long[] pos) {
        for (int d = 0; d < numDimensions(); d++) {
            if (pos[d] < min[d] || pos[d] > max[d]) {
                return false;
            }
        }
        return true;
    }
    public void unsafeLinearIndexToRectCoords(long index, long[] coords) {
        long remainder = index;
        for (int i = coords.length-1; i > 0; i--) {
            coords[i] = remainder / stride[i];
            remainder = remainder - coords[i]* stride[i];
        }
        coords[0] = remainder;
    }

    public long unsafeLinearIndexToRectCoord(long index, int d) {
        long[] coords = new long[shape.length];
        unsafeLinearIndexToRectCoords(index, coords);
        return coords[d];
    }

    public long rectCoordsToLinearIndex(long[] coords) {
        long index = 0;
        for (int i = 0; i < coords.length; i++) {
            index += coords[i] * stride[i];
        }
        return index;
    }

    public int rectCoordsToIntLinearIndex(long[] coords) {
        return (int) rectCoordsToLinearIndex(coords);
    }

    public void getPosition(long[] position) {
        System.arraycopy(currentPos, 0, position, 0, position.length);
    }

    public long getAxisPosition(int axis) {
        return currentPos[axis];
    }

    public void movePos(Localizable distance) {
        for (int d = 0; d < distance.numDimensions(); d++)
            currentPos[d] += distance.getLongPosition(d);
    }

    public void movePos(long[] distance) {
        for (int d = 0; d < distance.length; d++)
            currentPos[d] += distance[d];
    }

    public void movePos(int[] distance) {
        for (int d = 0; d < distance.length; d++)
            currentPos[d] += distance[d];
    }

    public void moveAxisPos(int axis, long distance) {
        currentPos[axis] += distance;
    }

    public void setPosition(Localizable position) {
        for (int d = 0; d < position.numDimensions(); d++)
            currentPos[d] = position.getLongPosition(d);
    }

    public void setPosition(long[] position) {
        System.arraycopy(position, 0, currentPos, 0, position.length);
    }

    public void setPosition(int[] position) {
        for (int d = 0; d < position.length; d++)
            currentPos[d] = position[d];
    }

    public void setAxisPos(int axis, long position) {
        currentPos[axis] = position;
    }

    public long minAxis(int axis) {
        return min[axis];
    }

    public long maxAxis(int axis) {
        return max[axis];
    }

    public int numDimensions() {
        return currentPos.length;
    }

    public long getSize() {
        return ImageAccessUtils.getMaxSize(min, max);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        RectIntervalHelper that = (RectIntervalHelper) o;

        return new EqualsBuilder()
                .append(shape, that.shape)
                .append(min, that.min)
                .append(max, that.max)
                .append(currentPos, that.currentPos)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(shape)
                .append(min)
                .append(max)
                .append(currentPos)
                .toHashCode();
    }
}
