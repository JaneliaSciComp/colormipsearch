package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * Creator of a ColorMIPQuerySearch for a given mask that generates a certain score type.
 *
 * @param <S> color depth match score type
 */
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
    ColorDepthSearchAlgorithm<S> createColorDepthSearchAlgorithm(ImageArray queryImage,
                                                                 Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers,
                                                                 int queryThreshold,
                                                                 int queryBorderSize,
                                                                 ColorDepthSearchParams cdsParams);

    default ColorDepthSearchAlgorithm<S> createColorDepthQuerySearchAlgorithmWithDefaultParams(
            ImageArray queryImage,
            Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers,
            int queryThreshold,
            int queryBorderSize) {
        return createColorDepthSearchAlgorithm(queryImage, queryVariantsSuppliers, queryThreshold, queryBorderSize, getDefaultCDSParams());
    }

}
