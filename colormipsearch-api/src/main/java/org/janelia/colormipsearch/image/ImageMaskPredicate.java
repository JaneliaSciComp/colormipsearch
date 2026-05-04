package org.janelia.colormipsearch.image;

/**
 * ImageMaskPredicate defines the conditions for a pixel to be masked.
 * These conditions can be based both on the position and on the value of the pixel.
 */
public interface ImageMaskPredicate {
    boolean checkPixelPos(ImageArray imageArray, int x, int y, int z);

    boolean checkPixelVal(int val);

    ImageMaskPredicate NO_MASKING = new ImageMaskPredicate() {
        @Override
        public boolean checkPixelPos(ImageArray imageArray, int x, int y, int z) {
            return false;
        }

        @Override
        public boolean checkPixelVal(int val) {
            return false;
        }
    };
}
