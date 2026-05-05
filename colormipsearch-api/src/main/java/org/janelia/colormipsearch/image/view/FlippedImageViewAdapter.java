package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.ImageArray;

public class FlippedImageViewAdapter extends AbstractImageViewAdapter {

    private final int flippedAxes;

    public FlippedImageViewAdapter(int flippedAxes) {
        this.flippedAxes = flippedAxes;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getPackedIntValAtCoords(imageArray, x, y, z);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getChannelIntValAtCoords(imageArray, x, y, z, ch);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        int sourceX = Dimensions.isX(flippedAxes) ? imageArray.getWidth() - x - 1 : x;
        int sourceY = Dimensions.isY(flippedAxes) ? imageArray.getHeight() - y - 1 : y;
        int sourceZ = Dimensions.isZ(flippedAxes) ? imageArray.getDepth() - z - 1 : z;
        return imageArray.getPackedIntValAtCoords(sourceX, sourceY, sourceZ);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        int sourceX = Dimensions.isX(flippedAxes) ? imageArray.getWidth() - x - 1 : x;
        int sourceY = Dimensions.isY(flippedAxes) ? imageArray.getHeight() - y - 1 : y;
        int sourceZ = Dimensions.isZ(flippedAxes) ? imageArray.getDepth() - z - 1 : z;
        return imageArray.getChannelIntValAtCoords(sourceX, sourceY, sourceZ, ch);
    }
}
