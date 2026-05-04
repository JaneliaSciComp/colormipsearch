package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a short array. Suitable for 16-bit images.
 */
public class ShortImageArray extends AbstractWriteableImageArray<short[]> {

    public ShortImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }

    @Override
    protected short[] allocatePixelData() {
        return new short[getSpatialSize() * getChannels()];
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            // Note that per channel operations are still supported.
            throw new UnsupportedOperationException("Cannot pack multiple channels into a single value");
        } else {
            return getChannelIntValAtIndex(pi, 0);
        }
    }

    @Override
    public void setPackedIntValAtIndex(int pi, int val) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            // Note that per channel operations are still supported.
            throw new UnsupportedOperationException("Cannot pack multiple channels into a single value");
        } else {
            setChannelIntValAtIndex(pi, 0, val);
        }
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        return pixelData[channelOffsets[ch] + pi] & 0xFFFF;
    }

    @Override
    public void setChannelIntValAtIndex(int pi, int ch, int val) {
        pixelData[channelOffsets[ch] + pi] = (short) (val & 0xFFFF);
    }
}
