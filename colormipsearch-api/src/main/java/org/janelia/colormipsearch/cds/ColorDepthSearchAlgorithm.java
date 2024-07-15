package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * ColorMIPMaskCompare encapsulates a query image and it provides a method to search
 * the enclosed query in other target images.
 * @param <S> score type
 * @param <P> RGB pixel type
 * @param <G> Gray (Intensity) pixel type
 */
public interface ColorDepthSearchAlgorithm<S extends ColorDepthMatchScore, P extends RGBPixelType<P>, G extends IntegerType<G>> extends Serializable {

    /**
     * @return query image from the current context.
     */
    RandomAccessibleInterval<P> getQueryImage();

    /**
     * @return required variant RGB types for calculating the score.
     */
    Set<ComputeFileType> getRequiredTargetRGBVariantTypes();

    /**
     * @return required variant Gray types for calculating the score.
     */
    Set<ComputeFileType> getRequiredTargetGrayVariantTypes();

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
     * @param targetImage target image to be searched using the current queryImage
     * @param targetRGBVariantsSuppliers RGB target image supplier per variant type. The map key is the variant type and the value is
     *                                   the supplier that can provide the corresponding image.
     * @param targetGrayVariantsSuppliers
     * @return
     */
    S calculateMatchingScore(@Nonnull RandomAccessibleInterval<P> targetImage,
                             Map<ComputeFileType, Supplier<RandomAccessibleInterval<P>>> targetRGBVariantsSuppliers,
                             Map<ComputeFileType, Supplier<RandomAccessibleInterval<G>>> targetGrayVariantsSuppliers);
}
