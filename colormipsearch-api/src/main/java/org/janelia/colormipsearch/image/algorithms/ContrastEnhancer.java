package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.imageprocessing.ImageStats;

/**
 * Histogram stretching and scaling utilities for single-channel images.
 */
public class ContrastEnhancer {

    public static ImageArray enhanceContrastUsingZProjection(ImageArray imageArray) {
        ImageArray zProjection = ImageOperations.maxIntensityProjection(imageArray, 0, imageArray.getDepth(), Gray16ImageArray::new);
        ImageArray contrastEnhancedZProjection = ImageOperations.stretchHistogram(zProjection, 0.35);
        ImageStats zProjectionStats = ImageOperations.getImageMinMax(contrastEnhancedZProjection);
        if (zProjectionStats.maxVal > 0 && zProjectionStats.maxVal != 255) {
            double scale = (zProjectionStats.maxVal != zProjectionStats.minVal)
                    ? 255.0 / (zProjectionStats.maxVal - zProjectionStats.minVal)
                    : 255.0 / zProjectionStats.maxVal;
            double offset = -zProjectionStats.minVal * scale;
            return ImageOperations.scaleIntensity(imageArray, 0., 255., scale, offset);
        } else {
            return imageArray;
        }
    }

    /**
     * Scale all non-zero pixel values from [0, srcMaxIntensity] to [0, dstMaxIntensity].
     *
     * @param image            writeable single-channel image
     * @param srcMaxIntensity  source max intensity
     * @param dstMaxIntensity  destination max intensity
     */
    public static void scaleHistogramRight(WriteableImageArray image, int srcMaxIntensity, int dstMaxIntensity) {
        if (srcMaxIntensity == 0) {
            return;
        }
        int totalPixels = image.getSpatialSize();
        for (int i = 0; i < totalPixels; i++) {
            int value = image.getPackedIntValAtIndex(i);
            if (value > 0) {
                double scaledValue = (double) dstMaxIntensity * value / srcMaxIntensity;
                if (scaledValue > dstMaxIntensity) {
                    scaledValue = dstMaxIntensity;
                }
                image.setPackedIntValAtIndex(i, Math.round((float) scaledValue));
            }
        }
    }
}
