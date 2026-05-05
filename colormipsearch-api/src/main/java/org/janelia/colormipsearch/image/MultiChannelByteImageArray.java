package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a byte array. Suitable for 8-bit images.
 */
public class MultiChannelByteImageArray extends AbstractByteImageArray {

    public MultiChannelByteImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }
}
