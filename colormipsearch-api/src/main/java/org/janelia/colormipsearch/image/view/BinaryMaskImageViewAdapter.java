package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class BinaryMaskImageViewAdapter extends AbstractImageViewAdapter {

    private final int threshold;
    private final int foreground;
    private final int background;

    public BinaryMaskImageViewAdapter(int threshold, int foreground, int background) {
        this.threshold = threshold;
        this.foreground = foreground;
        this.background = background;
    }

    @Override
    public int getChannels(ImageArray imageArray) {
        return 1;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int p = imageArray.getPackedIntValAtIndex(pi);
        return p > threshold ? foreground : background;
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        if (ch > 0) {
            throw new IllegalArgumentException("Invalid channel " + ch);
        } else {
            return getPackedIntValAtIndex(imageArray, pi);
        }
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        return getPackedIntValAtIndex(imageArray, imageArray.getSpatialLinearIndex(x, y, z));
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        if (ch > 0) {
            throw new IllegalArgumentException("Invalid channel " + ch);
        } else {
            return getPackedIntValAtIndex(imageArray, imageArray.getSpatialLinearIndex(x, y, z));
        }
    }
}
