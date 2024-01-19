package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.Localizable;

public class RectCoordsHelper {

    private final long[] shape;
    private final long[] min;
    private final long[] max;
    private final long[] currentPos;
    private final long[] stride;
    private int currentDim;

    public RectCoordsHelper(long[] shape) {
        this(ImageAccessUtils.getZero(shape.length), ImageAccessUtils.getMax(shape), shape);
    }

    public RectCoordsHelper(Interval interval) {
        this(interval.minAsLongArray(), interval.maxAsLongArray(), interval.dimensionsAsLongArray());
    }

    public RectCoordsHelper(long[] min, long[] max, long[] shape) {
        this(min, max, shape, min, 0);
    }

    private RectCoordsHelper(long[] min, long[] max, long[] shape, long[] currentPos, int currentDim) {
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

    public RectCoordsHelper copy() {
        return new RectCoordsHelper(min, max, shape, currentPos, currentDim);
    }

    public long[] linearIndexToRectCoords(long index) {
        long[] coords = new long[shape.length];
        unsafeLinearIndexToRectCoords(index, coords);
        return coords;
    }

    public void unsafeLinearIndexToRectCoords(long index, long[] coords) {
        assert coords.length == shape.length;
        long remainder = index;
        for (int i = coords.length-1; i > 0; i--) {
            coords[i] = remainder / stride[i];
            remainder = remainder - coords[i]* stride[i];
        }
        coords[0] = remainder;
        assert coords[0] < shape[0];
    }

    public long unsafeLinearIndexToRectCoord(long index, int d) {
        assert d < shape.length;
        long[] coords = new long[shape.length];
        unsafeLinearIndexToRectCoords(index, coords);
        return coords[d];
    }

    public long rectCoordsToLinearIndex(long[] coords) {
        assert coords.length == shape.length;
        long index = 0;
        for (int i = 0; i < coords.length; i++) {
            index += coords[i] * stride[i];
        }
        return index;
    }

    public int rectCoordsToIntLinearIndex(long[] coords) {
        return (int) rectCoordsToLinearIndex(coords);
    }

    public void nextPos() {
        // increment current rank
        currentPos[currentDim]++;
        if (currentPos[currentDim] >= max[currentDim]) {
            // currentDim reached the max value,
            // so we need to find the next rank that needs to be incremented
            for (int nextRank = currentDim + 1;nextRank < currentPos.length; nextRank++) {
                currentPos[nextRank]++;
                if (currentPos[nextRank] < max[nextRank]) {
                    // found the next rank => reset all lower ranks
                    for (int d = 0; d < nextRank; d++) {
                        currentPos[d] = min[d];
                    }
                    // reset currentDim
                    currentDim = 0;
                    break;
                }
            }
        }
    }

    public boolean hasNextPos() {
        return currentPos[currentPos.length - 1] < max[currentPos.length - 1];
    }

    public void resetPos() {
        for (int d = 0; d < min.length; d++)
            currentPos[d] = min[d];
        currentDim = 0;
    }

    public void currentPos(long[] position) {
        assert position.length <= currentPos.length;
        for (int d = 0; d < position.length; d++)
            position[d] = currentPos[d];
    }

    public long currentAxisPos(int axis) {
        assert axis < currentPos.length;
        return currentPos[axis];
    }

    public void movePos(Localizable distance) {
        assert distance.numDimensions() < currentPos.length;
        for (int d = 0; d < distance.numDimensions(); d++)
            currentPos[d] += distance.getLongPosition(d);
    }

    public void movePos(long[] distance) {
        assert distance.length <= currentPos.length;
        for (int d = 0; d < distance.length; d++)
            currentPos[d] += distance[d];
    }

    public void movePos(int[] distance) {
        assert distance.length <= currentPos.length;
        for (int d = 0; d < distance.length; d++)
            currentPos[d] += distance[d];
    }

    public void moveAxisPos(int axis, long distance) {
        currentPos[axis] += distance;
    }

    public void setPosition(Localizable position) {
        assert position.numDimensions() < currentPos.length;
        for (int d = 0; d < position.numDimensions(); d++)
            currentPos[d] = position.getLongPosition(d);
    }

    public void setPosition(long[] position) {
        assert position.length <= currentPos.length;
        for (int d = 0; d < position.length; d++)
            currentPos[d] = position[d];
    }

    public void setPosition(int[] position) {
        assert position.length <= currentPos.length;
        for (int d = 0; d < position.length; d++)
            currentPos[d] = position[d];
    }

    public void setAxisPos(int axis, long position) {
        assert axis < currentPos.length;
        currentPos[axis] = position;
    }

    public int numDimensions() {
        return currentPos.length;
    }

    public long getSize() {
        return ImageAccessUtils.getMaxSize(min, max);
    }
}
