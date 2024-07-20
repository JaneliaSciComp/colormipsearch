package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Map;
import java.util.function.Supplier;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
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
     * @param cdsParams color depth search parameters encapsulated in the algorithm. These could be mask specific
     *                  or global parameters
     * @return a color depth query search instance for the given query
     */
    ColorDepthSearchAlgorithm<S> createColorDepthSearchAlgorithm(
            RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
            Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantsSuppliers,
            int queryThreshold,
            ColorDepthSearchParams cdsParams);

    default ColorDepthSearchAlgorithm<S> createColorDepthQuerySearchAlgorithmWithDefaultParams(
            RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
            Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantsSuppliers,
            int queryThreshold) {
        return createColorDepthSearchAlgorithm(queryImage, queryVariantsSuppliers, queryThreshold, new ColorDepthSearchParams());
    }

}
