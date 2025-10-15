package org.janelia.colormipsearch;

import java.util.function.BiPredicate;

import ij.ImagePlus;
import org.janelia.colormipsearch.imageprocessing.ImageArrayUtils;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.imageprocessing.LImage;

public class ImageTestUtils {

    public static ImageRegionDefinition getExcludedRegions() {
        return img -> {
            int imgWidth = img.getWidth();
            BiPredicate<Integer, Integer> colorScaleLabelRegion;
            if (imgWidth > 270) {
                colorScaleLabelRegion = (x, y) -> x >= imgWidth - 270 && y < 90;
            } else {
                colorScaleLabelRegion = (x, y) -> false;
            }
            BiPredicate<Integer, Integer> nameLabelRegion = (x, y) -> x < 330 && y < 100;
            return colorScaleLabelRegion.or(nameLabelRegion);
        };
    }

    public static void display(String title, LImage lImage) {
        ImagePlus imp = new ImagePlus(title, ImageArrayUtils.toImageProcessor(lImage.toImageArray()));
        imp.show();
    }

}
