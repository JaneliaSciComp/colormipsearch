package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.imageprocessing.ImageStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Histogram stretching and scaling utilities for single-channel images.
 */
public class ContrastEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ContrastEnhancer.class);

    public static ImageStats enhancedContrastMIPStats(ImageArray imageArray, double saturated) {
        ImageArray mip = ImageOperations.maxIntensityProjection(imageArray, 0, imageArray.getDepth(), Gray16ImageArray::new);
        ImageArray contrastEnhancedMIP = ImageOperations.stretchHistogram(mip, saturated);
        ImageStats mipStats = ImageOperations.getImageStats(contrastEnhancedMIP);
        LOG.info("MIP stats: {}", mipStats);
        return mipStats;
    }

    public static ImageArray enhanceContrastUsingMIP(ImageArray imageArray) {
        ImageStats mipStats = enhancedContrastMIPStats(imageArray, 0.35);
        if (mipStats.maxVal > 0 && mipStats.maxVal != 255) {
            double scale = (mipStats.maxVal != mipStats.minVal)
                    ? 255.0 / (mipStats.maxVal - mipStats.minVal)
                    : 255.0 / mipStats.maxVal;
            double offset = -mipStats.minVal * scale;
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
