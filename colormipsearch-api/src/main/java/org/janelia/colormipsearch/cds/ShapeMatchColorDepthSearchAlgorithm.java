package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.PixelConverter;
import org.janelia.colormipsearch.image.RGBToIntensityPixelConverter;
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

    private final ImageAccess<? extends RGBPixelType<?>> queryImage;
    private final ImageAccess<UnsignedByteType> querySignal;
    private final ImageAccess<UnsignedByteType> overexpressedQueryMask;
    private final int queryThreshold;
    private final ImageTransforms.QuadTupleFunction<UnsignedByteType, ? extends RGBPixelType<?>, UnsignedIntType, ? extends RGBPixelType<?>, UnsignedIntType> pixelGapOp;
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
        this.querySignal = ImageTransforms.createRGBToSignalTransformation(queryImage, 2);
        this.overexpressedQueryMask = createMaskForOverExpressedRegions(queryImage);
        this.pixelGapOp = (querySignal, queryPix, targetGrad, targetDilated) -> {
            if (querySignal.get() == 0 || targetGrad.getInt() == 0 || targetDilated.isZero()) {
                return targetGrad;
            }

            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(
                    queryPix.getRed(), queryPix.getGreen(), queryPix.getBlue(),
                    targetDilated.getRed(), targetDilated.getGreen(), targetDilated.getBlue()
            );

            if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                return new UnsignedIntType(pxGapSlice - DEFAULT_COLOR_FLUX);
            } else {
                return targetGrad;
            }
        };
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
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageAccess<? extends RGBPixelType<?>> targetImage,
                                                  Map<ComputeFileType, Supplier<ImageAccess<? extends RGBPixelType<?>>>> rgbVariantsSuppliers,
                                                  Map<ComputeFileType, Supplier<ImageAccess<UnsignedIntType>>> grayVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageAccess<UnsignedIntType> targetGradientImage = getAnyVariantImage(
                grayVariantsSuppliers.get(ComputeFileType.GradientImage),
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
        ImageAccess<? extends RGBPixelType<?>> targetZGapMaskImage = getRGBVariantImage(
                rgbVariantsSuppliers.get(ComputeFileType.ZGapImage),
                computedTargetZGapMaskImage
        );
        ShapeMatchScore shapeScore = calculateNegativeScores(
                queryImage,
                targetImage,
                targetGradientImage,
                targetZGapMaskImage,
                false);

        if (mirrorQuery) {
            ShapeMatchScore mirroredShapedScore = calculateNegativeScores(
                    ImageTransforms.createMirrorTransformation(queryImage, 0),
                    targetImage,
                    targetGradientImage,
                    targetZGapMaskImage,
                    true
            );

            if (mirroredShapedScore.getScore() < shapeScore.getScore()) {
                return mirroredShapedScore;
            }
        }

        return shapeScore;
    }

    private ImageAccess<? extends RGBPixelType<?>> getRGBVariantImage(Supplier<ImageAccess<? extends RGBPixelType<?>>> variantImageSupplier,
                                                                      ImageAccess<? extends RGBPixelType<?>> defaultImageAccess) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return defaultImageAccess;
        }
    }

    private <T> ImageAccess<T> getAnyVariantImage(Supplier<ImageAccess<T>> variantImageSupplier,
                                                  ImageAccess<T> defaultImageAccess) {
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
                                                    ImageAccess<UnsignedIntType> targetGradientImage,
                                                    ImageAccess<? extends RGBPixelType<?>> targetZGapMaskImage,
                                                    boolean mirroredMask) {
        long startTime = System.currentTimeMillis();

        long gradientAreaGap = 0; // !!!!! FIXME
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = 0; // !!!!!! FIXME
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, mirroredMask);

    }

    @SuppressWarnings("unchecked")
    private <T extends RGBPixelType<T>> ImageAccess<UnsignedByteType> createMaskForOverExpressedRegions(ImageAccess<? extends RGBPixelType<?>> img) {
        // create a 60 px dilation and a 20px dilation
        // if the 20px dilation is 0 where the 60px dilation isn't then this is an overexpressed region (mark it as 1)
        ImageAccess<T> r1Dilation = ImageTransforms.createHyperSphereDilationTransformation((ImageAccess<T>) img, 60);
        ImageAccess<T> r2Dilation = ImageTransforms.createHyperSphereDilationTransformation((ImageAccess<T>) img, 20);
        PixelConverter<T, UnsignedByteType> rgbToSignal =
                new RGBToIntensityPixelConverter<T>(false)
                    .andThen(p -> p.get() > 0 ? ImageTransforms.SIGNAL : ImageTransforms.NO_SIGNAL);
        return ImageTransforms.createBinaryOpTransformation(
                r1Dilation,
                r2Dilation,
                (p1, p2) -> {
                    if (p2.isNotZero()) {
                        return ImageTransforms.NO_SIGNAL;
                    } else {
                        return rgbToSignal.convert(p1);
                    }
                }
        );

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
