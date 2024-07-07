package org.janelia.colormipsearch.cds;

import java.util.Arrays;
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
import org.janelia.colormipsearch.image.QuadConverter;
import org.janelia.colormipsearch.image.RGBPixelHistogram;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Shape2DMatchColorDepthSearchAlgorithm<P extends RGBPixelType<P>, G extends IntegerType<G>> implements ColorDepthSearchAlgorithm<ShapeMatchScore, P, G> {

    static <P extends RGBPixelType<P>> ImageAccess<P> createMaskForPotentialRegionsWithHighExpression(ImageAccess<P> img, int r1, int r2) {
        // create 2 dilation - one for r1 and one for r2 and "subtract" them
        // the operation is not quite a subtraction but the idea is
        // to mask pixels from the first dilation that are non zero in the second dilation
        long[] r1s = new long[img.numDimensions()];
        Arrays.fill(r1s, r1);
        long[] r2s = new long[img.numDimensions()];
        Arrays.fill(r2s, r2);

        ImageAccess<P> r1Dilation = ImageTransforms.createHyperSphereDilationTransformation(
                img,
                () -> new RGBPixelHistogram<>(img.getBackgroundValue()),
                r1s);
        ImageAccess<P> r2Dilation = ImageTransforms.createHyperSphereDilationTransformation(
                img,
                () -> new RGBPixelHistogram<>(img.getBackgroundValue()),
                r2s);
        ImageAccess<P> diffR1R2 = ImageTransforms.createBinaryPixelTransformation(
                r1Dilation,
                r2Dilation,
                (p1, p2, res) -> {
                    // mask pixels from the r1 dilation if they are present in the r2 dilation
                    // this is close to a r1Dilation - r2Dilation but not quite
                    if (p2.isNotZero()) {
                        res.setZero();
                    } else {
                        res.set(p1);
                    }
                },
                img.getBackgroundValue()
        );
        return ImageAccessUtils.materialize(diffR1R2, null);
    }

    private static <P extends RGBPixelType<P>, G extends IntegerType<G>> QuadConverter<UnsignedByteType, P, G, P, G> createPixelGapOperator() {
        return (querySignal, queryPix, targetGrad, targetDilated, gapPixel) -> {
            if (queryPix.isNotZero() && targetDilated.isNotZero()) {
                int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(
                        queryPix.getRed(), queryPix.getGreen(), queryPix.getBlue(),
                        targetDilated.getRed(), targetDilated.getGreen(), targetDilated.getBlue()
                );
                if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                    gapPixel.setInteger(applyGapThreshold(pxGapSlice -DEFAULT_COLOR_FLUX));
                    return;
                }
            }
            gapPixel.setInteger(applyGapThreshold(querySignal.getInteger() * targetGrad.getInteger()));
        };
    }

    private static int applyGapThreshold(int gapValue) {
        return gapValue > GAP_THRESHOLD ? gapValue : 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(Shape2DMatchColorDepthSearchAlgorithm.class);
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

    private final ImageAccess<P> queryImageAccess;
    private final ImageAccess<?> queryROIMask;
    private final ImageAccess<UnsignedByteType> querySignalAccess;
    private final ImageAccess<UnsignedByteType> overexpressedQueryRegionsAccess;
    private final int targetThreshold;
    private final boolean mirrorQuery;
    private final int negativeRadius;

    Shape2DMatchColorDepthSearchAlgorithm(ImageAccess<P> queryImage,
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
                                                  Map<ComputeFileType, Supplier<ImageAccess<P>>> targetRGBVariantsSuppliers,
                                                  Map<ComputeFileType, Supplier<ImageAccess<G>>> targetGrayVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageAccess<G> targetGradientImage = getVariantImage(
                targetGrayVariantsSuppliers.get(ComputeFileType.GradientImage),
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
                targetRGBVariantsSuppliers.get(ComputeFileType.ZGapImage),
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
                            ImageTransforms.createMirrorImage(queryImageAccess, 0),
                            queryROIMask),
                    ImageTransforms.maskPixelsUsingMaskImage(
                            ImageTransforms.createMirrorImage(querySignalAccess, 0),
                            queryROIMask),
                    ImageTransforms.maskPixelsUsingMaskImage(
                            ImageTransforms.createMirrorImage(overexpressedQueryRegionsAccess, 0),
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

    private <T extends IntegerType<T>> ImageAccess<T> getVariantImage(Supplier<ImageAccess<T>> variantImageSupplier,
                                                                      ImageAccess<T> defaultImageAccess) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return defaultImageAccess;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RGBPixelType<T>> ImageAccess<T> getDilation(ImageAccess<? extends RGBPixelType<?>> img) {
        long[] negativeRadii = new long[img.numDimensions()];
        Arrays.fill(negativeRadii, negativeRadius);
        return ImageTransforms.createHyperSphereDilationTransformation(
                (ImageAccess<T>) img,
                () -> new RGBPixelHistogram<>((T) img.getBackgroundValue()),
                negativeRadii
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

        ImageAccess<G> gapsImage = ImageTransforms.createQuadPixelTransformation(
                querySignalImage, queryImage, targetGradientImage, targetZGapMaskImage,
                createPixelGapOperator(),
                targetGradientImage.getBackgroundValue()
        );
        ImageAccess<UnsignedByteType> overexpressedTargetRegions = ImageTransforms.createBinaryPixelTransformation(
                targetImage,
                overexpressedQueryRegions,
                (p1, p2, target) -> {
                    if (p2.get() > 0) {
                        int r1 = p1.getRed();
                        int g1 = p1.getGreen();
                        int b1 = p1.getBlue();
                        // if any channel is > threshold, mark the pixel as signal
                        if (r1 > targetThreshold || g1 > targetThreshold || b1 > targetThreshold) {
                            target.set(1);
                            return;
                        }
                    }
                    target.set(0);
                },
                new UnsignedByteType(0)
        );
        long gradientAreaGap = ImageAccessUtils.fold(gapsImage,
                0L, (a, p) -> a + p.getInteger(), (p1, p2) -> p1 + p2);
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = ImageAccessUtils.fold(overexpressedTargetRegions,
                0L, (a, p) -> a + p.getInteger(), (a1, a2) -> a1 + a2);
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, mirroredMask);
    }

    private ImageAccess<UnsignedByteType> createMaskForOverExpressedRegions(ImageAccess<P> img) {
        // create a 60 px dilation and a 20px dilation
        // if the 20px dilation is 0 where the 60px dilation isn't then this is an overexpressed region (mark it as 1)
        ImageAccess<P> candidateRegionsForHighExpression = createMaskForPotentialRegionsWithHighExpression(img, 60, 20);
        return ImageTransforms.createRGBToSignalTransformation(candidateRegionsForHighExpression, 0);
    }

}
