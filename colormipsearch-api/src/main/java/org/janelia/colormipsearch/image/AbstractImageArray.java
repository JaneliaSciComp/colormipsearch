package org.janelia.colormipsearch.image;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Base class for readable image array implementations.
 */
public abstract class AbstractImageArray implements ImageArray {

    private final int width;
    private final int height;
    private final int depth;
    private final int spatialSize;
    private final int channels;

    protected AbstractImageArray(int width, int height, int depth, int channels) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.channels = channels;
        this.spatialSize = width * height * depth;
    }

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
