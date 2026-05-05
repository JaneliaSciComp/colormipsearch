package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a short array. Suitable for 16-bit images.
 */
public class Gray16ImageArray extends AbstractWriteableImageArray<short[]> {

    public Gray16ImageArray(int width, int height, int depth) {
        super(width, height, depth, 1);
    }

    @Override
    protected short[] allocatePixelData() {
        return new short[getSpatialSize() * getChannels()];
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        return getChannelIntValAtIndex(pi, 0);
    }

    @Override
    public void setPackedIntValAtIndex(int pi, int val) {
        setChannelIntValAtIndex(pi, 0, val);
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        assert ch == 0;
        // channel is hardcoded to 0 anyway
        return pixelData[channelOffsets[0] + pi] & 0xFFFF;
    }

    @Override
    public void setChannelIntValAtIndex(int pi, int ch, int val) {
        assert ch == 0;
        // channel is hardcoded to 0 anyway
        pixelData[channelOffsets[0] + pi] = (short) (val & 0xFFFF);
    }
}
