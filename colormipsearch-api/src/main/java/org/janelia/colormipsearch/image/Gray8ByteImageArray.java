package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a byte array. Suitable for 8-bit images.
 */
public class Gray8ByteImageArray extends AbstractByteImageArray {

    public Gray8ByteImageArray(int width, int height, int depth) {
        super(width, height, depth, 1);
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        return pixelData[channelOffsets[0] + pi] & 0xFF;
    }

    @Override
    public void setPackedIntValAtIndex(int pi, int val) {
        pixelData[channelOffsets[0] + pi] = (byte) (val & 0xFF);
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        assert ch == 0;
        return pixelData[channelOffsets[0] + pi] & 0xFF;
    }

    @Override
    public void setChannelIntValAtIndex(int pi, int ch, int val) {
        assert ch == 0;
        pixelData[channelOffsets[0] + pi] = (byte) (val & 0xFF);
    }
}
