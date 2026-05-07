package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class ScaleValuesImageViewAdapter extends AbstractImageViewAdapter {

    private final double scaleFactor;
    private final int maxIntensity;

    public ScaleValuesImageViewAdapter(double scaleFactor, int maxIntensity) {
        this.scaleFactor = scaleFactor;
        this.maxIntensity = maxIntensity;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int val = imageArray.getPackedIntValAtIndex(pi);
        return scaleValue(val);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int val = imageArray.getChannelIntValAtIndex(pi, ch);
        return scaleValue(val);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        int val = imageArray.getPackedIntValAtCoords(x, y, z);
        return scaleValue(val);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        int val = imageArray.getChannelIntValAtCoords(x, y, z, ch);
        return scaleValue(val);
    }

    private int scaleValue(int val) {
        if (val > 0) {
            double scaledValue = scaleFactor * val;
            if (scaledValue > maxIntensity) {
                return maxIntensity;
            } else {
                return (int) Math.round(scaledValue);
            }
        } else {
            return val;
        }
    }

}
