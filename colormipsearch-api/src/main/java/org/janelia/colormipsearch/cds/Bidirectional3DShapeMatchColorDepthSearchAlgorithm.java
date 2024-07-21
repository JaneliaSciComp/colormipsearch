package org.janelia.colormipsearch.cds;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<ShapeMatchScore> {


    private final RandomAccessibleInterval<? extends RGBPixelType<?>> queryImageAccess;
    private final RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask;
    private final VolumeSegmentationHelper volumeSegmentationHelper;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                       Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantsSuppliers,
                                                       RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
                                                       String alignmentSpace,
                                                       int queryThreshold,
                                                       int targetThreshold,
                                                       boolean withMirrorFlag) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        this.queryImageAccess = applyThreshold(queryImage, queryThreshold);
        this.queryROIMask = queryROIMask;
        this.volumeSegmentationHelper = new VolumeSegmentationHelper(
                alignmentSpace,
                getFirstReifiableVariant(
                        queryVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                        queryVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
                )
        );
    }

    @Override
    public RandomAccessibleInterval<? extends RGBPixelType<?>> getQueryImage() {
        return queryImageAccess;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return Collections.emptySet();
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        return new ShapeMatchScore(-1, -1, -1, false); // !!! FIXME
    }

}
