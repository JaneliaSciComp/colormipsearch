package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public abstract class AbstractImageViewAdapter implements ImageViewAdapter {

    @Override
    public int getWidth(ImageArray imageArray) {
        return imageArray.getWidth();
    }

    @Override
    public int getHeight(ImageArray imageArray) {
        return imageArray.getHeight();
    }

    @Override
    public int getDepth(ImageArray imageArray) {
        return imageArray.getDepth();
    }

    @Override
    public int getChannels(ImageArray imageArray) {
        return imageArray.getChannels();
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        return imageArray.getPackedIntValAtIndex(pi);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        return imageArray.getChannelIntValAtIndex(pi, ch);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        return imageArray.getPackedIntValAtCoords(x, y, z);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        return imageArray.getChannelIntValAtCoords(x, y, z, ch);
    }
}
