package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class ShapeMatchColorDepthSearchAlgorithm implements ColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final Logger LOG = LoggerFactory.getLogger(ShapeMatchColorDepthSearchAlgorithm.class);
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

//    private static final TriFunction<Integer, Integer, Integer, Integer> PIXEL_GAP_OP = (gradScorePix, maskPix, dilatedPix) -> {
//        if ((maskPix & 0xFFFFFF) != 0 && (dilatedPix & 0xFFFFFF) != 0) {
//            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(maskPix, dilatedPix);
//            if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
//                // negative score value
//                return pxGapSlice - DEFAULT_COLOR_FLUX;
//            }
//        }
//        return gradScorePix;
//    };

    private final ImageAccess<? extends RGBPixelType<?>> queryImage;
    private final int queryThreshold;
    private final boolean mirrorQuery;
    private final int negativeRadius;

    ShapeMatchColorDepthSearchAlgorithm(ImageAccess<? extends RGBPixelType<?>> queryImage,
                                        int queryThreshold,
                                        boolean mirrorQuery,
                                        int negativeRadius) {
        this.queryImage = queryImage;
        this.queryThreshold = queryThreshold;
        this.mirrorQuery = mirrorQuery;
        this.negativeRadius = negativeRadius;
    }

    @Override
    public ImageAccess<? extends RGBPixelType<?>> getQueryImage() {
        return queryImage;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.GradientImage, ComputeFileType.ZGapImage);
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     * The gradient image must be non-null but the z-gap image can be null in which case it is calculated using
     * a dilation transformation.
     *
     * @param targetImage
     * @param variantImageSuppliers
     * @return
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageAccess<? extends RGBPixelType<?>> targetImage,
                                                  Map<ComputeFileType, Supplier<ImageAccess<? extends RGBPixelType<?>>>> variantImageSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageAccess<? extends RGBPixelType<?>> targetGradientImage = getVariantImage(
                variantImageSuppliers.get(ComputeFileType.GradientImage),
                null
        );
        if (targetGradientImage == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ImageAccess<? extends RGBPixelType<?>> thresholdedTarget = ImageTransforms.createThresholdedMaskTransformation(
                targetImage,
                queryThreshold
        );
        ImageAccess<? extends RGBPixelType<?>> computedTargetZGapMaskImage = getDilation(thresholdedTarget);
        ImageAccess<? extends RGBPixelType<?>> targetZGapMaskImage = getVariantImage(
                variantImageSuppliers.get(ComputeFileType.ZGapImage),
                computedTargetZGapMaskImage
        );
        ShapeMatchScore shapeScore = calculateNegativeScores(
                queryImage,
                targetImage,
                targetGradientImage,
                targetZGapMaskImage,
                false);

        //        LImage targetImage = LImageUtils.create(targetImageArray).mapi(clearLabels);
//        LImage targetGradientImage = LImageUtils.create(targetGradientImageArray);
//        LImage targetZGapMaskImage = targetZGapMaskImageArray != null
//                ? LImageUtils.create(targetZGapMaskImageArray)
//                : negativeRadiusDilation.applyTo(targetImage.map(ColorTransformation.mask(queryThreshold)));
//
//
//        if (mirrorQuery) {
//            LOG.trace("Start calculating area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
//            ShapeMatchScore mirrorNegativeScores = calculateNegativeScores(targetImage, targetGradientImage, targetZGapMaskImage, ImageTransformation.horizontalMirror(), true);
//            LOG.trace("Completed area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
//            if (mirrorNegativeScores.getScore() < negativeScores.getScore()) {
//                return mirrorNegativeScores;
//            }
//        }
//        return negativeScores;

        return shapeScore;
    }

    private ImageAccess<? extends RGBPixelType<?>> getVariantImage(
            Supplier<ImageAccess<? extends RGBPixelType<?>>> variantImageSupplier,
            ImageAccess<? extends RGBPixelType<?>> defaultImageAccess) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return defaultImageAccess;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RGBPixelType<T>> ImageAccess<T> getDilation(ImageAccess<? extends RGBPixelType<?>> img) {
        return ImageTransforms.createHyperSphereDilationTransformation(
                (ImageAccess<T>) img,
                negativeRadius
        );
    }

    private ShapeMatchScore calculateNegativeScores(ImageAccess<? extends RGBPixelType<?>> queryImage,
                                                    ImageAccess<? extends RGBPixelType<?>> targetImage,
                                                    ImageAccess<? extends RGBPixelType<?>> targetGradientImage,
                                                    ImageAccess<? extends RGBPixelType<?>> targetZGapMaskImage,
                                                    boolean mirroredMask) {
        long startTime = System.currentTimeMillis();

        long gradientAreaGap = 0; // !!!!! FIXME
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = 0; // !!!!!! FIXME
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, mirroredMask);

    }

//    private ShapeMatchScore calculateNegativeScores(LImage targetImage, LImage targetGradientImage, LImage targetZGapMaskImage, ImageTransformation maskTransformation, boolean useMirroredMask) {
//        long startTime = System.currentTimeMillis();
//        LImage queryROIImage;
//        LImage queryIntensitiesROIImage;
//        LImage queryHighExpressionMaskROIImage;
//        if (queryROIMaskImage == null) {
//            queryROIImage = queryImage.mapi(maskTransformation);
//            queryIntensitiesROIImage = queryIntensityValues.mapi(maskTransformation);
//            queryHighExpressionMaskROIImage = queryHighExpressionMask.mapi(maskTransformation);
//        } else {
//            queryROIImage = LImageUtils.combine2(
//                    queryImage.mapi(maskTransformation),
//                    queryROIMaskImage,
//                    (p1, p2) -> ColorTransformation.mask(queryImage.getPixelType(), p1, p2));
//            queryIntensitiesROIImage = LImageUtils.combine2(
//                    queryIntensityValues.mapi(maskTransformation),
//                    queryROIMaskImage,
//                    (p1, p2) -> ColorTransformation.mask(queryIntensityValues.getPixelType(), p1, p2));
//            queryHighExpressionMaskROIImage = LImageUtils.combine2(
//                    queryHighExpressionMask.mapi(maskTransformation),
//                    queryROIMaskImage,
//                    (p1, p2) -> ColorTransformation.mask(queryHighExpressionMask.getPixelType(), p1, p2));
//        }
//        LImage gaps = LImageUtils.combine4(
//                queryIntensitiesROIImage,
//                targetGradientImage,
//                queryROIImage,
//                targetZGapMaskImage.mapi(maskTransformation),
//                gapOp.andThen(gap -> gap > GAP_THRESHOLD ? gap : 0)
//        );
//        LImage highExpressionRegions = LImageUtils.combine2(
//                targetImage,
//                queryHighExpressionMaskROIImage,
//                (p1, p2) -> {
//                    if (p2 == 1) {
//                        int r1 = (p1 >> 16) & 0xff;
//                        int g1 = (p1 >> 8) & 0xff;
//                        int b1 = p1 & 0xff;
//                        if (r1 > queryThreshold || g1 > queryThreshold || b1 > queryThreshold) {
//                            return 1;
//                        }
//                    }
//                    return 0;
//                });
//        long gradientAreaGap = gaps.fold(0L, Long::sum);
//        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
//        long highExpressionArea = highExpressionRegions.fold(0L, Long::sum);
//        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
//        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, useMirroredMask);
//    }

}
