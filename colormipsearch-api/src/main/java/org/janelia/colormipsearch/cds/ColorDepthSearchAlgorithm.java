package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * ColorMIPMaskCompare encapsulates a query image and it provides a method to search
 * the enclosed query in other target images.
 * @param <S> score type
 */
public interface ColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> extends Serializable {

    /**
     * @return query image from the current context.
     */
    ImageAccess<? extends RGBPixelType<?>> getQueryImage();

    /**
     * @return required variant types for calculating the score.
     */
    Set<ComputeFileType> getRequiredTargetVariantTypes();

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
     * @param variantImageSuppliers image supplier per variant type. The map key is the variant type and the value is
     *                              the supplier that can provide the corresponding image.
     * @return
     */
    S calculateMatchingScore(@Nonnull ImageAccess<? extends RGBPixelType<?>> targetImage,
                             Map<ComputeFileType, Supplier<ImageAccess<? extends RGBPixelType<?>>>> variantImageSuppliers);
}
