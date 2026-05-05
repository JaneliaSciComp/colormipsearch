package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public abstract class AbstractSimpleMaxFilterImageViewAdapter extends AbstractImageViewAdapter {

    private final int xRadius;
    private final int yRadius;
    private final int zRadius;
    private final HyperEllipsoidMask kernel;

    AbstractSimpleMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        this.xRadius = xRadius;
        this.yRadius = yRadius;
        this.zRadius = zRadius;
        this.kernel = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
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
        updatePos(imageArray, x, y, z);
        return getVal();
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        updatePos(imageArray, x, y, z);
        return getChVal(ch);
    }

    abstract void resetChVals();

    abstract int getVal();

    abstract int getChVal(int ch);

    abstract void updateChVals(ImageArray imageArray, int pi);

    private void updatePos(ImageArray imageArray, int cx, int cy, int cz) {
        resetChVals();
        for (int z = Math.max(0, cz - zRadius); z < Math.min(imageArray.getDepth(), cz + zRadius + 1); z++) {
            int dz = z - cz;
            for (int y = Math.max(0, cy - yRadius); y < Math.min(imageArray.getHeight(), cy + yRadius + 1); y++) {
                int dy = y - cy;
                for (int x = Math.max(0, cx - xRadius); x < Math.min(imageArray.getWidth(), cx + xRadius + 1); x++) {
                    int dx = x - cx;
                    if (kernel.contains(dx, dy, dz)) {
                        int pi = imageArray.getSpatialLinearIndex(x, y, z);
                        updateChVals(imageArray, pi);
                    }
                }
            }
        }
    }
}
