package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
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
public class ShapeMatchColorDepthSearchAlgorithm<P extends RGBPixelType<P>, G extends IntegerType<G>> implements ColorDepthSearchAlgorithm<ShapeMatchScore, P, G> {

    private static <P extends RGBPixelType<P>, G extends IntegerType<G>> ImageTransforms.QuadTupleFunction<UnsignedByteType, P, G, P, G> createPixelGapOperator(G zeroGrayPixel) {
        return (querySignal, queryPix, targetGrad, targetDilated) -> {
            if (querySignal.get() == 0 || targetGrad.getInteger() == 0 || targetDilated.isZero()) {
                return applyGapThreshold(targetGrad, zeroGrayPixel);
            }

            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(
                    queryPix.getRed(), queryPix.getGreen(), queryPix.getBlue(),
                    targetDilated.getRed(), targetDilated.getGreen(), targetDilated.getBlue()
            );

            if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                G gap = zeroGrayPixel.createVariable();
                gap.setInteger(pxGapSlice - DEFAULT_COLOR_FLUX);
                return applyGapThreshold(gap, zeroGrayPixel);
            } else {
                return applyGapThreshold(targetGrad, zeroGrayPixel);
            }
        };
    }

    private static <G extends IntegerType<G>> G applyGapThreshold(G gap, G zero) {
        return gap.getInteger() > GAP_THRESHOLD ? gap : zero;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ShapeMatchColorDepthSearchAlgorithm.class);
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

    private final ImageAccess<P> queryImageAccess;
    private final ImageAccess<?> queryROIMask;
    private final ImageAccess<UnsignedByteType> querySignalAccess;
    private final ImageAccess<UnsignedByteType> overexpressedQueryRegionsAccess;
    private final int targetThreshold;
    private final boolean mirrorQuery;
    private final int negativeRadius;

    ShapeMatchColorDepthSearchAlgorithm(ImageAccess<P> queryImage,
                                        ImageAccess<?> queryROIMask,
                                        int queryThreshold,
                                        int targetThreshold,
                                        boolean mirrorQuery,
                                        int negativeRadius) {
        this.queryImageAccess = ImageTransforms.maskPixelsBelowThreshold(queryImage, queryThreshold);
        this.queryROIMask = queryROIMask;
        this.targetThreshold = targetThreshold;
        this.mirrorQuery = mirrorQuery;
        this.negativeRadius = negativeRadius;
        this.querySignalAccess = ImageTransforms.createRGBToSignalTransformation(queryImage, 2);
        this.overexpressedQueryRegionsAccess = createMaskForOverExpressedRegions(queryImage);
    }

    @Override
    public ImageAccess<P> getQueryImage() {
        return queryImageAccess;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetRGBVariantTypes() {
        return EnumSet.of(ComputeFileType.ZGapImage);
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetGrayVariantTypes() {
        return EnumSet.of(ComputeFileType.GradientImage);
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageAccess<P> targetImage,
                                                  Map<ComputeFileType, Supplier<ImageAccess<P>>> rgbVariantsSuppliers,
                                                  Map<ComputeFileType, Supplier<ImageAccess<G>>> grayVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageAccess<G> targetGradientImage = getVariantImage(
                grayVariantsSuppliers.get(ComputeFileType.GradientImage),
                null
        );
        if (targetGradientImage == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ImageAccess<P> thresholdedTarget = ImageTransforms.maskPixelsBelowThreshold(
                targetImage,
                targetThreshold
        );
        ImageAccess<P> computedTargetZGapMaskImage = getDilation(thresholdedTarget);
        ImageAccess<P> targetZGapMaskImage = getVariantImage(
                rgbVariantsSuppliers.get(ComputeFileType.ZGapImage),
                computedTargetZGapMaskImage
        );
        ShapeMatchScore shapeScore = calculateNegativeScores(
                queryImageAccess,
                querySignalAccess,
                overexpressedQueryRegionsAccess,
                targetImage,
                targetGradientImage,
                targetZGapMaskImage,
                false);

        if (mirrorQuery) {
            ShapeMatchScore mirroredShapedScore = calculateNegativeScores(
                    ImageTransforms.maskPixelsUsingMaskImage(
                            ImageTransforms.createMirrorTransformation(queryImageAccess, 0),
                            queryROIMask),
                    ImageTransforms.maskPixelsUsingMaskImage(
                            ImageTransforms.createMirrorTransformation(querySignalAccess, 0),
                            queryROIMask),
                    ImageTransforms.maskPixelsUsingMaskImage(
                            ImageTransforms.createMirrorTransformation(overexpressedQueryRegionsAccess, 0),
                            queryROIMask),
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

    private <T> ImageAccess<T> getVariantImage(Supplier<ImageAccess<T>> variantImageSupplier,
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

    private ShapeMatchScore calculateNegativeScores(ImageAccess<P> queryImage,
                                                    ImageAccess<UnsignedByteType> querySignalImage,
                                                    ImageAccess<UnsignedByteType> overexpressedQueryRegions,
                                                    ImageAccess<P> targetImage,
                                                    ImageAccess<G> targetGradientImage,
                                                    ImageAccess<P> targetZGapMaskImage,
                                                    boolean mirroredMask) {
        long startTime = System.currentTimeMillis();

        ImageAccess<G> gapsImage = ImageTransforms.createQuadOpTransformation(
                querySignalImage, queryImage, targetGradientImage, targetZGapMaskImage,
                createPixelGapOperator(targetGradientImage.getBackgroundValue())
        );
        ImageAccess<UnsignedByteType> overexpressedTargetRegions = ImageTransforms.createBinaryOpTransformation(
                targetImage, overexpressedQueryRegions,
                (p1, p2) -> {
                    if (p2.get() > 0) {
                        int r1 = p1.getRed();
                        int g1 = p1.getGreen();
                        int b1 = p1.getBlue();
                        if (r1 > targetThreshold || g1 > targetThreshold || b1 > targetThreshold) {
                            return ImageTransforms.SIGNAL;
                        }
                    }
                    return ImageTransforms.NO_SIGNAL;
                }
        );
        long gradientAreaGap = ImageAccessUtils.fold(gapsImage, 0L, (p, a) -> a + p.getInteger());
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = ImageAccessUtils.fold(overexpressedTargetRegions, 0L, (p, a) -> a + p.getInteger());
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

}
