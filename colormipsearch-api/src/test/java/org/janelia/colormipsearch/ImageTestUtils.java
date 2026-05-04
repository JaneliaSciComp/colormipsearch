package org.janelia.colormipsearch;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;

public class ImageTestUtils {

    public static ImageMaskPredicate getExcludedRegionsPredicate() {
        return new ImageMaskPredicate() {
            @Override
            public boolean checkPixelPos(ImageArray imageArray, int x, int y, int z) {
                int imgWidth = imageArray.getWidth();
                boolean isColorScaleLabelRegion = x >= imgWidth - 270 && y < 90;
                boolean isNameLabelRegion = x < 330 && y < 100;
                return isColorScaleLabelRegion || isNameLabelRegion;
            }

            @Override
            public boolean checkPixelVal(int val) {
                return false;
            }
        };
    }

}
