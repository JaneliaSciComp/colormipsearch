package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;

/**
 * N-dimensional max filter (morphological dilation) operating on ImageArray.
 */
public class MaxFilterAlgorithm {

    public static ImageArray maxGrayFilter3D(ImageArray input, int xRadius, int yRadius, int zRadius) {
        return ImageOperations.maxGrayFilter3D(input, xRadius, yRadius, zRadius);
    }

    /**
     * 2D RGB max filter in X and Y (zRadius=0).
     */
    public static ImageArray maxRGBFilter2D(ImageArray input, int radius) {
        return ImageOperations.maxRGBFilter2D(input, radius, radius);
    }

}
