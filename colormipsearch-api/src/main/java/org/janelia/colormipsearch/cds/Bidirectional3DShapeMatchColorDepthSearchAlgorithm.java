package org.janelia.colormipsearch.cds;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final int GAP_THRESHOLD = 3;

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
        long startTime = System.currentTimeMillis();
        RandomAccessibleInterval<? extends IntegerType<?>> target3DImage = getFirstReifiableVariant(
                targetVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                targetVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
        );
        RandomAccessibleInterval<? extends RGBPixelType<?>> targetSegmentedCDM = volumeSegmentationHelper.generateSegmentedCDM(
            target3DImage
        );
        if (targetSegmentedCDM == null) {
            // the 3D images are not available
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        RandomAccessibleInterval<UnsignedByteType> targetSignalCDM = ImageTransforms.rgbToSignalTransformation(
                targetSegmentedCDM, 1
        );
        Img<UnsignedShortType> queryGradientImg = (Img<UnsignedShortType>) DistanceTransformAlgorithm.generateDistanceTransform(
                queryImageAccess,
                5);

        RandomAccessibleInterval<UnsignedShortType> gapsImage = ImageTransforms.createBinaryPixelOperation(
                targetSignalCDM,
                queryGradientImg,
                (p1, p2, r) -> {
                    int p1Val = p1.get();
                    int p2Val = p1.get();
                    int gapVal = p1Val * p2Val;
                    r.set(gapVal > GAP_THRESHOLD ? gapVal : 0);
                },
                new UnsignedShortType()
        );

        long gradientAreaGap = ImageAccessUtils.fold(gapsImage,
                0L, (a, p) -> a + p.getInteger(), Long::sum
        );
        System.out.printf("Gradient area gap: %d\n", gradientAreaGap);

        return new ShapeMatchScore(-1, -1, -1, false); // !!! FIXME
    }

}
