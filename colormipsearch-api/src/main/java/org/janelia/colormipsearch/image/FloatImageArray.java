package org.janelia.colormipsearch.image;

/**
 * ImageArray backed by a float array. Suitable for distance transform results and other real-valued images.
 */
public class FloatImageArray extends AbstractWriteableImageArray<float[]> {

    public FloatImageArray(int width, int height, int depth) {
        super(width, height, depth, 1);
    }

    @Override
    protected float[] allocatePixelData() {
        return new float[getSpatialSize() * getChannels()];
    }

    @Override
    public float getPackedFloatValAtIndex(int pi) {
        return getChannelFloatValAtIndex(pi, 0);
    }

    @Override
    public void setPackedFloatValAtIndex(int pi, float val) {
        setChannelFloatValAtIndex(pi, 0, val);
    }

    @Override
    public float getChannelFloatValAtIndex(int pi, int ch) {
        assert ch == 0;
        // channel is hardcoded to 0 anyway
        return pixelData[channelOffsets[0] + pi];
    }

    @Override
    public void setChannelFloatValAtIndex(int pi, int ch, float val) {
        assert ch == 0;
        // channel is hardcoded to 0 anyway
        pixelData[channelOffsets[0] + pi] = val;
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
