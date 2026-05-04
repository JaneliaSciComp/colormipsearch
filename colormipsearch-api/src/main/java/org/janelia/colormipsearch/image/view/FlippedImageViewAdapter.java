package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class FlippedImageViewAdapter extends AbstractImageViewAdapter {

    public static int NONE = 0;
    public static int X_AXIS = 0x1;
    public static int Y_AXIS = 0x2;
    public static int Z_AXIS = 0x4;

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
        int sourceX = (flippedAxes & X_AXIS) != 0 ? imageArray.getWidth() - x - 1 : x;
        int sourceY = (flippedAxes & Y_AXIS) != 0 ? imageArray.getHeight() - y - 1 : y;
        int sourceZ = (flippedAxes & Z_AXIS) != 0 ? imageArray.getDepth() - z - 1 : z;
        return imageArray.getPackedIntValAtCoords(sourceX, sourceY, sourceZ);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        int sourceX = (flippedAxes & X_AXIS) != 0 ? imageArray.getWidth() - x - 1 : x;
        int sourceY = (flippedAxes & Y_AXIS) != 0 ? imageArray.getHeight() - y - 1 : y;
        int sourceZ = (flippedAxes & Z_AXIS) != 0 ? imageArray.getDepth() - z - 1 : z;
        return imageArray.getChannelIntValAtCoords(sourceX, sourceY, sourceZ, ch);
    }
}
