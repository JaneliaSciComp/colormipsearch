package org.janelia.colormipsearch.image;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Handles conversions from cartesian coords to a flat index and vice-versa.
 */
public class IntervalHelper {

    private final int[] shape;
    private final int[] min;
    private final int[] max;
    private final int[] absoluteStrides;
    private final int[] relativeStrides;

    public IntervalHelper(int[] shape) {
        this(new int[shape.length], CoordUtils.maxCoords(shape), shape);
    }

    public IntervalHelper(int[] min, int[] max, int[] shape) {
        this.min = min.clone();
        this.max = max.clone();
        this.shape = shape.clone();
        this.absoluteStrides = new int[shape.length];
        this.relativeStrides = new int[shape.length];
        setStrides(shape, absoluteStrides);
        setStrides(this.getDims(), relativeStrides);
    }

    private void setStrides(int[] forShape, int[] strides) {
        int strideAccumulator = 1;
        for (int d = 0; d < strides.length; d++) {
            strides[d] = strideAccumulator;
            if (forShape[d] == 0) {
                throw new IllegalArgumentException("Invalid shape axis dimension: dimension for " + d + " axis is 0");
            }
            strideAccumulator *= forShape[d];
        }
    }

    public int[] relativeLinearIndexToRelativeRectCoords(int index) {
        int[] coords = new int[shape.length];
        int remainder = index;
        for (int i = coords.length - 1; i > 0; i--) {
            coords[i] = remainder / relativeStrides[i];
            remainder = remainder - coords[i] * relativeStrides[i];
        }
        coords[0] = remainder;
        return coords;
    }

    public int relativeRectCoordsToAbsoluteLinearIndex(int[] coords) {
        int index = 0;
        for (int i = 0; i < coords.length; i++) {
            index += (min[i] + coords[i]) * absoluteStrides[i];
        }
        return index;
    }

    public int getDim(int axis) {
        return max[axis] - min[axis] + 1;
    }

    public int[] getDims() {
        int[] dims = new int[shape.length];
        for (int d = 0; d < shape.length; d++) {
            dims[d] = getDim(d);
        }
        return dims;
    }

    public int getMin(int axis) {
        return min[axis];
    }

    public int getMax(int axis) {
        return max[axis];
    }

    public int getSize() {
        int sz = 1;
        for (int d = 0; d < min.length && d < max.length; d++) {
            sz *= (max[d] - min[d] + 1);
        }
        return sz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntervalHelper that = (IntervalHelper) o;
        return new EqualsBuilder()
                .append(shape, that.shape)
                .append(min, that.min)
                .append(max, that.max)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(shape)
                .append(min)
                .append(max)
                .toHashCode();
    }
}
