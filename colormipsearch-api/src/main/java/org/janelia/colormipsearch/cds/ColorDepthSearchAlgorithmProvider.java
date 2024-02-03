package org.janelia.colormipsearch.cds;

import java.io.Serializable;

import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Creator of a ColorMIPQuerySearch for a given mask that generates a certain score type.
 *
 * @param <S> color depth match score type
 */
public interface ColorDepthSearchAlgorithmProvider<S extends ColorDepthMatchScore, P extends RGBPixelType<P>, G> extends Serializable {
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
     * @param cdsParams color depth search parameters encapsulated in the algorithm. These could be mask specific
     *                  or global parameters
     * @return a color depth query search instance for the given query
     */
    ColorDepthSearchAlgorithm<S, P, G> createColorDepthSearchAlgorithm(
            ImageAccess<P> queryImage,
            int queryThreshold,
            ColorDepthSearchParams cdsParams);

    default ColorDepthSearchAlgorithm<S, P, G> createColorDepthQuerySearchAlgorithmWithDefaultParams(
            ImageAccess<P> queryImage,
            int queryThreshold) {
        return createColorDepthSearchAlgorithm(queryImage, queryThreshold, new ColorDepthSearchParams());
    }

}
