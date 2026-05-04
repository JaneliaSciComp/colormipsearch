package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class TranslateImageViewAdapter extends AbstractImageViewAdapter {

    private final int tx;
    private final int ty;
    private final int tz;
    private final int background;

    public TranslateImageViewAdapter(int tx, int ty, int tz, int background) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.background = 0;
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
        // to get to source coordinates use inverse translations
        int nx = x - tx;
        if (nx < 0 || nx >= imageArray.getWidth()) return background;
        int ny = y - ty;
        if (ny < 0 || ny >= imageArray.getHeight()) return background;
        int nz = z - tz;
        if (nz < 0 || nz >= imageArray.getDepth()) return background;
        return imageArray.getPackedIntValAtCoords(nx, ny, nz);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        // to get to source coordinates use inverse translations
        int nx = x - tx;
        if (nx < 0 || nx >= imageArray.getWidth()) return background;
        int ny = y - ty;
        if (ny < 0 || ny >= imageArray.getHeight()) return background;
        int nz = z - tz;
        if (nz < 0 || nz >= imageArray.getDepth()) return background;
        return imageArray.getChannelIntValAtCoords(nx, ny, nz, ch);
    }
}
