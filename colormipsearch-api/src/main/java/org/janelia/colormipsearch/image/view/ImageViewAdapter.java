package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public interface ImageViewAdapter {

    int getWidth(ImageArray imageArray);

    int getHeight(ImageArray imageArray);

    int getDepth(ImageArray imageArray);

    int getChannels(ImageArray imageArray);

    int getPackedIntValAtIndex(ImageArray imageArray, int pi);

    int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch);

    int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z);

    int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch);

    default float getPackedFloatValAtIndex(ImageArray imageArray, int pi) {
        return (float) getPackedIntValAtIndex(imageArray, pi);
    }

    default float getPackedFloatValAtCoords(ImageArray imageArray, int x, int y, int z) {
        return (float) getPackedIntValAtCoords(imageArray, x, y, z);
    }

    default float getChannelFloatValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        return (float) getChannelIntValAtCoords(imageArray, x, y, z, ch);
    }
}
