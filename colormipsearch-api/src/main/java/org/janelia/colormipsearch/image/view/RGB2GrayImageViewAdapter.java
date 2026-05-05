package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ColorOperations;
import org.janelia.colormipsearch.image.ImageArray;

public class RGB2GrayImageViewAdapter extends AbstractImageViewAdapter {

    private final RGBConverter pixelConverter;

    public RGB2GrayImageViewAdapter(RGBConverter pixelConverter) {
        this.pixelConverter = pixelConverter;
    }

    @Override
    public int getChannels(ImageArray imageArray) {
        return 1;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int r = imageArray.getChannelIntValAtIndex(pi, 0);
        int g = imageArray.getChannelIntValAtIndex(pi, 1);
        int b = imageArray.getChannelIntValAtIndex(pi, 2);
        return ColorOperations.rgb2Gray8(r, g, b);
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
