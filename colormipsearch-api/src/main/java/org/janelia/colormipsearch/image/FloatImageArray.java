package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a float array. Suitable for distance transform results and other real-valued images.
 */
public class FloatImageArray extends AbstractImageArray<float[]> {

    public FloatImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
    }

    @Override
    protected float[] allocatePixelData() {
        return new float[getSpatialSize() * getChannels()];
    }

    @Override
    public int getIntVal(int pi) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "getIntVal is not supported for multichannel FloatImageArray: " + getChannels());
        }
        return (int) pixelData[pi];
    }

    @Override
    public void setIntVal(int pi, int val) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "setIntVal is not supported for multichannel FloatImageArray: " + getChannels());
        }
        pixelData[pi] = val;
    }

    @Override
    public int getChannelVal(int pi, int ch) {
        return (int) pixelData[channelOffsets[ch] + pi];
    }

    @Override
    public void setChannelVal(int pi, int ch, int val) {
        pixelData[channelOffsets[ch] + pi] = val;
    }

    @Override
    public float getRealVal(int pi) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "getRealVal is not supported for multichannel FloatImageArray: " + getChannels());
        }
        return pixelData[pi];
    }

    @Override
    public void setRealVal(int pi, float val) {
        if (getChannels() > 1) {
            throw new UnsupportedOperationException(
                    "setRealVal is not supported for multichannel FloatImageArray: " + getChannels());
        }
        pixelData[pi] = val;
    }

    @Override
    public ImageArrayFactory getFactory() {
        return FloatImageArray::new;
    }
}
