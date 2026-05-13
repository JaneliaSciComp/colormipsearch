package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Map;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.mips.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * Creator of a ColorMIPQuerySearch for a given mask that generates a certain score type.
 *
 * @param <S> color depth match score type
 */
public interface ColorDepthSearchAlgorithmProvider<S extends ColorDepthMatchScore> extends Serializable {
    /**
     * This method is essentially a constructor for a ColorMIPQuerySearch
     * for the given query
     *
     * @param queryImage encapsulated query image
     * @return a color depth query search instance for the given query
     */
    ColorDepthSearchAlgorithm<S> createColorDepthSearchAlgorithm(ImageArray queryImage,
                                                                 Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers);

}
