package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a byte array. Suitable for 8-bit images.
 */
public class RGBByteImageArray extends AbstractByteImageArray {

    public RGBByteImageArray(int width, int height, int depth) {
        super(width, height, depth, 3);
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        int r = pixelData[channelOffsets[0] + pi] & 0xFF;
        int g = pixelData[channelOffsets[1] + pi] & 0xFF;
        int b = pixelData[channelOffsets[2] + pi] & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void setPackedIntValAtIndex(int pi, int val) {
        pixelData[channelOffsets[0] + pi] = (byte) ((val >> 16) & 0xFF);
        pixelData[channelOffsets[1] + pi] = (byte) ((val >> 8) & 0xFF);
        pixelData[channelOffsets[2] + pi] = (byte) (val & 0xFF);
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        assert ch < 3;
        return pixelData[channelOffsets[ch] + pi] & 0xFF;
    }

    @Override
    public void setChannelIntValAtIndex(int pi, int ch, int val) {
        assert ch < 3;
        pixelData[channelOffsets[ch] + pi] = (byte) (val & 0xFF);
    }
}
