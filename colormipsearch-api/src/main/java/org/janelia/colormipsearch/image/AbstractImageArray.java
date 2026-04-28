package org.janelia.colormipsearch.image;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Base class for image array implementations.
 *
 * @param <T> pixel data array type (byte[], short[], float[], etc.)
 */
public abstract class AbstractImageArray<T> implements ImageArray {

    private final int width;
    private final int height;
    private final int depth;
    private final int spatialSize;
    private final int channels;
    final T pixelData;
    final int[] channelOffsets;

    protected AbstractImageArray(int width, int height, int depth, int channels) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.channels = channels;
        this.spatialSize = width * height * depth;
        this.pixelData = allocatePixelData();
        this.channelOffsets = new int[channels];
        for (int c = 0; c < channels; c++) {
            channelOffsets[c] = c * spatialSize;
        }
    }

    protected abstract T allocatePixelData();

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public int getChannels() {
        return channels;
    }

    @Override
    public int getSpatialSize() {
        return spatialSize;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("width", width)
                .append("height", height)
                .append("depth", depth)
                .append("channels", channels)
                .toString();
    }
}
