package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;

/**
 * Histogram stretching and scaling utilities for single-channel images.
 */
public class ContrastEnhancer {

    /**
     * Stretch the histogram of a single-channel image with saturation control.
     * Zero-valued pixels are left unchanged.
     *
     * @param image          writeable single-channel image
     * @param saturated      percentage of pixels allowed to saturate (applied to upper end only, halved internally)
     * @param maxIntensity   output max intensity; if negative, auto-detected from image
     * @param minIntensity   output min intensity; if negative, auto-detected from image
     */
    public static void stretchHistogram(WriteableImageArray image, double saturated, int maxIntensity, int minIntensity) {
        int totalPixels = image.getSpatialSize();
        long upperPixelCount = (long) (totalPixels * (100.0 - saturated * 0.5) / 100.0);

        // Build histogram
        int[] bins = new int[65536];
        for (int i = 0; i < totalPixels; i++) {
            int val = image.getPackedIntValAtIndex(i);
            if (val >= 0 && val < 65536) {
                bins[val]++;
            }
        }

        // Determine default min/max and upper threshold
        int defMinIntensity = 0;
        int defMaxIntensity = 0;
        int upperThreshold = 0;
        long count = 0;
        for (int i = 0; i < 65536; i++) {
            int bin = bins[i];
            count += bin;
            if (count >= upperPixelCount && upperThreshold == 0) {
                upperThreshold = i;
            }
            if (bin > 0) {
                if (defMinIntensity == 0) {
                    defMinIntensity = i;
                }
                defMaxIntensity = i;
            }
        }

        if (maxIntensity < 0) {
            maxIntensity = defMaxIntensity;
        }
        if (minIntensity < 0) {
            minIntensity = defMinIntensity;
        }

        if (upperThreshold <= minIntensity) {
            return;
        }

        // Stretch — only modify non-zero pixels
        for (int i = 0; i < totalPixels; i++) {
            int value = image.getPackedIntValAtIndex(i);
            if (value > 0) {
                if (value >= upperThreshold) {
                    image.setPackedIntValAtIndex(i, maxIntensity);
                } else {
                    double scaledValue = (double) (maxIntensity * (value - defMinIntensity)) / (double) (upperThreshold - defMinIntensity);
                    image.setPackedIntValAtIndex(i, (int) scaledValue);
                }
            }
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
