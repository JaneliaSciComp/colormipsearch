package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.ImageArray;

public class SimpleRGBMaxFilterImageViewAdapter extends AbstractSimpleMaxFilterImageViewAdapter {

    private int redChMaxVal;
    private int greenChMaxVal;
    private int blueChMaxVal;

    public SimpleRGBMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        super(xRadius, yRadius, zRadius);
    }

    void resetChVals() {
        redChMaxVal = 0;
        greenChMaxVal = 0;
        blueChMaxVal = 0;
    }

    int getVal() {
        return redChMaxVal << 16 | greenChMaxVal << 8 | blueChMaxVal;
    }

    int getChVal(int ch) {
        return Dimensions.selectDim(redChMaxVal, greenChMaxVal, blueChMaxVal, ch);
    }

    void updateChVals(ImageArray imageArray, int pi) {
        redChMaxVal = Math.max(redChMaxVal, imageArray.getChannelIntValAtIndex(pi, 0));
        greenChMaxVal = Math.max(greenChMaxVal, imageArray.getChannelIntValAtIndex(pi, 1));
        blueChMaxVal = Math.max(blueChMaxVal, imageArray.getChannelIntValAtIndex(pi, 2));
    }
}
