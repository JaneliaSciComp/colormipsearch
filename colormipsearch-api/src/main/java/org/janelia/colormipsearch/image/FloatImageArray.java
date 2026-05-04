package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a float array. Suitable for distance transform results and other real-valued images.
 */
public class FloatImageArray extends AbstractWriteableImageArray<float[]> {

    public FloatImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }

    @Override
    protected float[] allocatePixelData() {
        return new float[getSpatialSize() * getChannels()];
    }

    @Override
    public float getPackedFloatValAtIndex(int pi) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            throw new UnsupportedOperationException("Cannot pack multiple channels into a single value");
        } else {
            return getChannelFloatValAtIndex(pi, 0);
        }
    }

    @Override
    public void setPackedFloatValAtIndex(int pi, float val) {
        int nChannels = getChannels();
        if (nChannels > 1) {
            // Note that per channel operations are still supported.
            throw new UnsupportedOperationException("Cannot pack multiple channels into a single value");
        } else {
            setChannelFloatValAtIndex(pi, 0, val);
        }
    }

    @Override
    public float getChannelFloatValAtIndex(int pi, int ch) {
        return pixelData[channelOffsets[ch] + pi];
    }

    @Override
    public void setChannelFloatValAtIndex(int pi, int ch, float val) {
        pixelData[channelOffsets[ch] + pi] = val;
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        return (int) getPackedFloatValAtIndex(pi);
    }

    @Override
    public void setPackedIntValAtIndex(int pi, int val) {
        setPackedFloatValAtIndex(pi, val);
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        return (int) getChannelFloatValAtIndex(pi, ch);
    }

    @Override
    public void setChannelIntValAtIndex(int pi, int ch, int val) {
        setChannelFloatValAtIndex(pi, ch, val);
    }

}
