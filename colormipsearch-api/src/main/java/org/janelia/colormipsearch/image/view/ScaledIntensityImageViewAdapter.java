package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class ScaledIntensityImageViewAdapter extends AbstractImageViewAdapter {

    private final double minValue;
    private final double maxValue;
    private final double offset;
    private final double scaleFactor;

    public ScaledIntensityImageViewAdapter(double minValue, double maxValue, double scaleFactor, double offset) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.scaleFactor = scaleFactor;
        this.offset = offset;
    }

    public ScaledIntensityImageViewAdapter(int sourceMax, int targetMax) {
        this.minValue = 0;
        this.maxValue = targetMax;
        this.scaleFactor = (double) targetMax / sourceMax;
        this.offset = 0;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        return (int) getPackedFloatValAtIndex(imageArray, pi);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        return (int) getPackedFloatValAtCoords(imageArray, x, y, z);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        return (int) getChannelFloatValAtIndex(imageArray, pi, ch);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        return (int) getChannelFloatValAtCoords(imageArray, x, y, z, ch);
    }

    @Override
    public float getPackedFloatValAtIndex(ImageArray imageArray, int pi) {
        float val = imageArray.getPackedFloatValAtIndex(pi);
        return (float) scaleValue(val);
    }

    @Override
    public float getPackedFloatValAtCoords(ImageArray imageArray, int x, int y, int z) {
        float val = imageArray.getPackedFloatValAtCoords(x, y, z);
        return (float) scaleValue(val);
    }

    @Override
    public float getChannelFloatValAtIndex(ImageArray imageArray, int pi, int ch) {
        float val = imageArray.getChannelFloatValAtIndex(pi, ch);
        return (float) scaleValue(val);
    }

    @Override
    public float getChannelFloatValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        float val = imageArray.getChannelFloatValAtCoords(x, y, z, ch);
        return (float) scaleValue(val);
    }

    private double scaleValue(double val) {
        double scaledVal = Math.round(val * scaleFactor + offset);
        if (scaledVal < minValue)
            return minValue;
        if (scaledVal > maxValue) {
            return maxValue;
        }
        return scaledVal;
    }

}
