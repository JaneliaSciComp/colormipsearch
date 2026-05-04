package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayVisitor;

public class MaxFilterImageViewAdapter extends AbstractImageViewAdapter {

    public static class RGBMaxImageArrayVisitor implements ImageArrayVisitor {
        final int[] chMaxVals;

        public RGBMaxImageArrayVisitor() {
            chMaxVals = new int[] {0, 0, 0};
        }

        @Override
        public void init() {
            chMaxVals[0] = 0; // red
            chMaxVals[1] = 0; // green
            chMaxVals[2] = 0; // blue
        }

        @Override
        public void visitPos(ImageArray imageArray, int x, int y, int z) {
            int pi = imageArray.getSpatialLinearIndex(x, y, z);
            chMaxVals[0] = Math.max(chMaxVals[0], imageArray.getChannelIntValAtIndex(pi, 0));
            chMaxVals[1] = Math.max(chMaxVals[1], imageArray.getChannelIntValAtIndex(pi, 1));
            chMaxVals[2] = Math.max(chMaxVals[2], imageArray.getChannelIntValAtIndex(pi, 2));
        }

        @Override
        public void visitPosCh(ImageArray imageArray, int x, int y, int z, int ch) {
            int pi = imageArray.getSpatialLinearIndex(x, y, z);
            chMaxVals[ch] = Math.max(chMaxVals[ch], imageArray.getChannelIntValAtIndex(pi, ch));
        }

        @Override
        public int getVal() {
            return chMaxVals[0] << 16 | chMaxVals[1] << 8 | chMaxVals[2];
        }

        @Override
        public int getChVal(int ch) {
            return chMaxVals[ch];
        }
    }

    public static class SingleChannelMaxImageArrayVisitor implements ImageArrayVisitor {
        int maxVal;

        public SingleChannelMaxImageArrayVisitor() {
            maxVal = 0;
        }

        @Override
        public void init() {
            maxVal = 0;
        }

        @Override
        public void visitPos(ImageArray imageArray, int x, int y, int z) {
            int pi = imageArray.getSpatialLinearIndex(x, y, z);
            maxVal = Math.max(maxVal, imageArray.getPackedIntValAtIndex(pi));
        }

        @Override
        public void visitPosCh(ImageArray imageArray, int x, int y, int z, int ch) {
            assert ch == 0;
            int pi = imageArray.getSpatialLinearIndex(x, y, z);
            maxVal = Math.max(maxVal, imageArray.getChannelIntValAtIndex(pi, ch));
        }

        @Override
        public int getVal() {
            return maxVal;
        }

        @Override
        public int getChVal(int ch) {
            assert ch == 0;
            return maxVal;
        }
    }

    private final int xRadius;
    private final int yRadius;
    private final int zRadius;
    private final HyperEllipsoidMask kernel;
    private final ImageArrayVisitor kernelVisitor;

    public MaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius,
                                     ImageArrayVisitor kernelVisitor) {
        this.xRadius = xRadius;
        this.yRadius = yRadius;
        this.zRadius = zRadius;
        this.kernelVisitor = kernelVisitor;
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
        return visitPos(imageArray, x, y, z);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        return visitPosCh(imageArray, x, y, z, ch);
    }

    private int visitPos(ImageArray imageArray, int cx, int cy, int cz) {
        kernelVisitor.init();
        for (int z = Math.max(0, cz - zRadius); z < Math.min(imageArray.getDepth(), cz + zRadius + 1); z++) {
            int dz = Math.abs(z - cz);
            for (int y = Math.max(0, cy - yRadius); y < Math.min(imageArray.getHeight(), cy + yRadius + 1); y++) {
                int dy = Math.abs(y - cy);
                for (int x = Math.max(0, cx - xRadius); x < Math.min(imageArray.getWidth(), cx + xRadius + 1); x++) {
                    int dx = Math.abs(x - cx);
                    if (kernel.contains(dx, dy, dz)) {
                        kernelVisitor.visitPos(imageArray, x, y, z);
                    }
                }
            }
        }
        return kernelVisitor.getVal();
    }

    private int visitPosCh(ImageArray imageArray, int cx, int cy, int cz, int ch) {
        kernelVisitor.init();
        for (int z = Math.max(0, cz - zRadius); z < Math.min(imageArray.getDepth(), cz + zRadius + 1); z++) {
            int dz = Math.abs(z - cz);
            for (int y = Math.max(0, cy - yRadius); y < Math.min(imageArray.getHeight(), cy + yRadius + 1); y++) {
                int dy = Math.abs(y - cy);
                for (int x = Math.max(0, cx - xRadius); x < Math.min(imageArray.getWidth(), cx + xRadius + 1); x++) {
                    int dx = Math.abs(x - cx);
                    if (kernel.contains(dx, dy, dz)) {
                        kernelVisitor.visitPosCh(imageArray, x, y, z, ch);
                    }
                }
            }
        }
        return kernelVisitor.getChVal(ch);
    }
}
