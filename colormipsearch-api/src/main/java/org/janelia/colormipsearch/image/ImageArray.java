package org.janelia.colormipsearch.image;

import java.io.Serializable;

/**
 * 3-dimensional multichannel image representation.
 * Pixel data is stored in planar layout: each channel is a contiguous block of spatialSize elements.
 */
public interface ImageArray extends Serializable {
    /**
     * @return image width
     */
    int getWidth();
    /**
     * @return image height
     */
    int getHeight();
    /**
     * @return image depth
     */
    int getDepth();
    /**
     * @return the number of channels
     */
    int getChannels();
    /**
     * Get an integer representation of all channels packed into a single value at linear index pi.
     */
    int getPackedIntValAtIndex(int pi);
    /**
     * Get channel value as an int at the specified linear index for specified channel.
     */
    int getChannelIntValAtIndex(int pi, int ch);
    /**
     * Get a float representation of all channels packed into a single value at linear index pi.
     */
    default float getPackedFloatValAtIndex(int pi) {
        return (float) getPackedIntValAtIndex(pi);
    }
    /**
     * Get a float representation of a channel value at linear index pi.
     */
    default float getChannelFloatValAtIndex(int pi, int ch) {
        return (float) getChannelIntValAtIndex(pi, ch);
    }
    /**
     * Get an integer representation of all channels packed into a single value at specified coordinates.
     */
    default int getPackedIntValAtCoords(int x, int y, int z) {
        return getPackedIntValAtIndex(getSpatialLinearIndex(x, y, z));
    }
    /**
     * Get an integer representation of a channel value at specified coordinates.
     */
    default int getChannelIntValAtCoords(int x, int y, int z, int ch) {
        return getChannelIntValAtIndex(getSpatialLinearIndex(x, y, z), ch);
    }
    /**
     * Get a float representation of all channels packed into a single value at specified coordinates.
     */
    default float getPackedFloatValAtCoords(int x, int y, int z) {
        return (float) getPackedIntValAtCoords(x, y, z);
    }
    /**
     * @return spatial linear index.
     */
    default int getSpatialLinearIndex(int x, int y, int z) {
        return z * getHeight() * getWidth() + y * getWidth() + x;
    }
    /**
     * Get a float representation of a channel value at specified coordinates.
     */
    default float getChannelFloatValAtCoords(int x, int y, int z, int ch) {
        return (float) getChannelIntValAtCoords(x, y, z, ch);
    }
    /**
     * @return an int array that contains spatial dimensions: dimX, dimY, dimZ
     */
    default int[] getSpatialDims() {
        return new int[] {
                getWidth(), getHeight(), getDepth()
        };
    }
    /**
     * @return total number of spatial voxels.
     * This will differ from the total size if there are more than 1 channel.
     */
    default int getSpatialSize() {
        return getWidth() * getHeight() * getDepth();
    }
}
