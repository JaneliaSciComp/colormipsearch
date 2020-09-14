package org.janelia.colormipsearch.api.cdsearch;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.janelia.colormipsearch.api.imageprocessing.ImageArray;

/**
 * ColorMIPMaskCompare encapsulates a query image and it provides a method to search
 * the enclosed query in other target images.
 */
public interface ColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> {

    ImageArray getQueryImage();

    /**
     * Score color depth matches between the current query (the one from the context of the current instance) and
     * the targetImageArray parameter.
     * An implementation must compare each pixel from the query with each pixel from the target.
     * An implementation, if needed, may filter query pixes to be above a specified query threshold as well as
     * the target pixels to be above a target threshold.
     * An implementation may use mirroring and/or x-y translation in order to increase the number of matching positions.
     * The gradient of the target image or the zgap mask of the target, if they are available, could
     * be used to calculate
     * calculate the negative impact of certain pixels to the total matching score.
     *
     * @param targetImageArray
     * @param targetGradientImageArray
     * @param targetZGapMaskImageArray
     * @return
     */
    S calculateMatchingScore(@Nonnull ImageArray targetImageArray,
                             @Nullable ImageArray targetGradientImageArray,
                             @Nullable ImageArray targetZGapMaskImageArray);

}
