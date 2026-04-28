package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a byte array. Suitable for 8-bit images.
 */
public class ByteImageArray extends AbstractImageArray<byte[]> {

    public ByteImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }

    @Override
    protected byte[] allocatePixelData() {
        return new byte[getSpatialSize() * getChannels()];
    }

    @Override
    public int getIntVal(int pi) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            int p = 0;
            for (int c = 0; c < nChannels; c++) {
                int channelVal = pixelData[channelOffsets[c] + pi] & 0xFF;
                p = (p << 8) | channelVal;
            }
            return p;
        } else {
            return pixelData[pi] & 0xFF;
        }
    }

    @Override
    public void setIntVal(int pi, int val) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            for (int c = 0; c < nChannels; c++) {
                int channelVal = (val >> (8 * (nChannels - 1 - c))) & 0xFF;
                pixelData[channelOffsets[c] + pi] = (byte) channelVal;
            }
        } else {
            pixelData[pi] = (byte) (val & 0xFF);
        }
    }

    @Override
    public int getChannelVal(int pi, int ch) {
        return pixelData[channelOffsets[ch] + pi] & 0xFF;
    }

    @Override
    public void setChannelVal(int pi, int ch, int val) {
        pixelData[channelOffsets[ch] + pi] = (byte) (val & 0xFF);
    }

    @Override
    public float getRealVal(int pi) {
        return getIntVal(pi);
    }

    @Override
    public void setRealVal(int pi, float val) {
        setIntVal(pi, (int) val);
    }

    @Override
    public ImageArrayFactory getFactory() {
        return ByteImageArray::new;
    }
}
