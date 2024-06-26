package org.janelia.colormipsearch.api_v2.cdsearch;

import java.io.Serializable;

import org.janelia.colormipsearch.imageprocessing.ImageArray;

/**
 * Creator of a ColorMIPQuerySearch for a given mask that generates a certain score type.
 * @see org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider
 *
 * @param <S> color depth match score type
 */
@Deprecated
public interface ColorDepthSearchAlgorithmProvider<S extends ColorDepthMatchScore> extends Serializable {
    /**
     * @return default color depth search parameters.
     */
    ColorDepthSearchParams getDefaultCDSParams();

    /**
     * This method is essentially a constructor for a ColorMIPQuerySearch
     * for the given query
     *
     * @param queryImage encapsulated query image
     * @param queryThreshold query image threshold
     * @param queryBorderSize
     * @param cdsParams color depth search parameters encapsulated in the algorithm. These could be mask specific
     *                  or global parameters
     * @return a color depth query search instance for the given query
     */
    ColorDepthSearchAlgorithm<S> createColorDepthQuerySearchAlgorithm(ImageArray<?> queryImage,
                                                                      int queryThreshold,
                                                                      int queryBorderSize,
                                                                      ColorDepthSearchParams cdsParams);

    default ColorDepthSearchAlgorithm<S> createColorDepthQuerySearchAlgorithmWithDefaultParams(ImageArray<?> queryImage, int queryThreshold, int queryBorderSize) {
        return createColorDepthQuerySearchAlgorithm(queryImage, queryThreshold, queryBorderSize, new ColorDepthSearchParams());
    }

}
