package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageStats;

public class ContrastEnhancedImageViewAdapter extends AbstractImageViewAdapter {

    final int minIntensity;
    final int maxIntensity;
    final int upperThreshold;

    /**
     * @param stats - image statistics
     * @param saturated - percentage of pixels allowed to saturate (applied to upper end only, halved internally)
     */
    public ContrastEnhancedImageViewAdapter(ImageStats stats, double saturated) {
        long upperPixelCount = (long) (stats.totalPixels * (100.0 - saturated * 0.5) / 100.0);
        // Determine default min/max and upper threshold
        int defMinIntensity = -1;
        int defMaxIntensity = 0;
        int currentUpperThreshold = 0;
        long count = 0;
        for (int i = 0; i < stats.histogram.length; i++) {
            int bin = stats.histogram[i];
            count += bin;
            if (count >= upperPixelCount && currentUpperThreshold == 0) {
                currentUpperThreshold = i;
            }
            if (bin > 0) {
                if (defMinIntensity < 0) {
                    defMinIntensity = i;
                }
                defMaxIntensity = i;
            }
        }
        if (defMinIntensity < 0) {
            // if all values are background
            defMinIntensity = 0;
        }
        upperThreshold = currentUpperThreshold;
        maxIntensity = defMaxIntensity;
        minIntensity = defMinIntensity;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int val = imageArray.getPackedIntValAtIndex(pi);
        return scaleValue(val);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int val = imageArray.getChannelIntValAtIndex(pi, ch);
        return scaleValue(val);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        int val = imageArray.getPackedIntValAtCoords(x, y, z);
        return scaleValue(val);
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        int val = imageArray.getChannelIntValAtCoords(x, y, z, ch);
        return scaleValue(val);
    }

    private int scaleValue(int val) {
        if (val == 0 || upperThreshold <= minIntensity) {
            // no saturated values
            return val;
        }
        if (val > upperThreshold) {
            return maxIntensity;
        } else {
            return (int) (((double) maxIntensity * (val - minIntensity)) / (double) (upperThreshold - minIntensity));
        }
    }

}
