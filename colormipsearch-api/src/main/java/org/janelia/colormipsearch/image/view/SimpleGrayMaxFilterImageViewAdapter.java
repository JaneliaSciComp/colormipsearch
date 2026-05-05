package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class SimpleGrayMaxFilterImageViewAdapter extends AbstractSimpleMaxFilterImageViewAdapter {

    private int chMaxVal;

    public SimpleGrayMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        super(xRadius, yRadius, zRadius);
    }

    void resetChVals() {
        chMaxVal = 0;
    }

    int getVal() {
        return chMaxVal;
    }

    int getChVal(int ch) {
        return chMaxVal;
    }

    void updateChVals(ImageArray imageArray, int pi) {
        chMaxVal = Math.max(chMaxVal, imageArray.getChannelIntValAtIndex(pi, 0));
    }
}
