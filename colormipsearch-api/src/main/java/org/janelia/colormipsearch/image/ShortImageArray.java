package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a short array. Suitable for 16-bit images.
 */
public class ShortImageArray extends AbstractImageArray<short[]> {

    public ShortImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }

    @Override
    protected short[] allocatePixelData() {
        return new short[getSpatialSize() * getChannels()];
    }

    @Override
    public int getIntVal(int pi) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "getIntVal is not supported for multichannel ShortImageArray: " + getChannels());
        }
        return pixelData[pi] & 0xFFFF;
    }

    @Override
    public void setIntVal(int pi, int val) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "setIntVal is not supported for multichannel ShortImageArray: " + getChannels());
        }
        pixelData[pi] = (short) (val & 0xFFFF);
    }

    @Override
    public int getChannelVal(int pi, int ch) {
        return pixelData[channelOffsets[ch] + pi] & 0xFFFF;
    }

    @Override
    public void setChannelVal(int pi, int ch, int val) {
        pixelData[channelOffsets[ch] + pi] = (short) (val & 0xFFFF);
    }

    @Override
    public float getRealVal(int pi) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "getRealVal is not supported for multichannel ShortImageArray: " + getChannels());
        }
        return pixelData[pi] & 0xFFFF;
    }

    @Override
    public void setRealVal(int pi, float val) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "setRealVal is not supported for multichannel ShortImageArray: " + getChannels());
        }
        pixelData[pi] = (short) ((int) val & 0xFFFF);
    }

    @Override
    public ImageArrayFactory getFactory() {
        return ShortImageArray::new;
    }
}
