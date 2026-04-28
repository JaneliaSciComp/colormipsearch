package org.janelia.colormipsearch.image;

import java.io.Serializable;

/**
 * N-dimensional image array representation with support for width, height, depth and multiple channels.
 * Pixel data is stored in planar layout: each channel is a contiguous block of spatialSize elements.
 */
public interface ImageArray extends Serializable {

    int getWidth();

    int getHeight();

    int getDepth();

    int getChannels();

    /**
     * Get an integer representation of all channels packed into a single int at spatial index pi.
     */
    int getIntVal(int pi);

    void setIntVal(int pi, int val);

    float getRealVal(int pi);

    void setRealVal(int pi, float val);

    int getChannelVal(int pi, int ch);

    void setChannelVal(int pi, int ch, int val);

    default int[] getSpatialDims() {
        return new int[]{getWidth(), getHeight(), getDepth()};
    }

    default int getSpatialSize() {
        return getWidth() * getHeight() * getDepth();
    }

    default int getIntPixel(int x, int y, int z) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        return getIntVal(idx);
    }

    default void setIntPixel(int x, int y, int z, int val) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        setIntVal(idx, val);
    }

    default int getChannelPixel(int x, int y, int z, int ch) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        return getChannelVal(idx, ch);
    }

    default void setChannelPixel(int x, int y, int z, int ch, int val) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        setChannelVal(idx, ch, val);
    }

    default float getRealPixel(int x, int y, int z) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        return getRealVal(idx);
    }

    default void setRealPixel(int x, int y, int z, float val) {
        int idx = z * getHeight() * getWidth() + y * getWidth() + x;
        setRealVal(idx, val);
    }

    ImageArrayFactory getFactory();

    // Convenience methods for backward compatibility with old imageprocessing.ImageArray API

    default int get(int pi) {
        return getIntVal(pi);
    }

    default void set(int pi, int val) {
        setIntVal(pi, val);
    }

    default int getPixel(int x, int y) {
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return getIntPixel(x, y, 0);
        }
        return 0;
    }

    default void setPixel(int x, int y, int p) {
        setIntPixel(x, y, 0, p);
    }

    default int getPixelCount() {
        return getSpatialSize();
    }
}
