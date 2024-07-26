package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.MirrorTransform;
import org.janelia.colormipsearch.image.QuadConverter;
import org.janelia.colormipsearch.image.RGBPixelHistogram;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Shape2DMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<ShapeMatchScore> {

    static <P extends RGBPixelType<P>> RandomAccessibleInterval<P> createMaskForPotentialRegionsWithHighExpression(RandomAccessibleInterval<? extends RGBPixelType<?>> img,
                                                                                                                   int r1, int r2) {
        // create 2 dilation - one for r1 and one for r2 and "subtract" them
        // the operation is not quite a subtraction but the idea is
        // to mask pixels from the first dilation that are non zero in the second dilation
        int[] r1s = new int[img.numDimensions()];
        Arrays.fill(r1s, r1);
        int[] r2s = new int[img.numDimensions()];
        Arrays.fill(r2s, r2);

        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<P> typedImg = (RandomAccessibleInterval<P>) img;
        P pxType = typedImg.randomAccess().get().createVariable();
        RandomAccessibleInterval<P> r1Dilation = ImageTransforms.dilateImage(
                typedImg,
                () -> new RGBPixelHistogram<>(pxType),
                r1s);
        RandomAccessibleInterval<P> r2Dilation = ImageTransforms.dilateImage(
                typedImg,
                () -> new RGBPixelHistogram<>(pxType),
                r2s);
        RandomAccessibleInterval<P> diffR1R2 = ImageTransforms.createBinaryPixelOperation(
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
                pxType
        );
        return ImageAccessUtils.materializeAsNativeImg(diffR1R2, null, pxType);
    }

    private static QuadConverter<? super IntegerType<?>,
            ? super IntegerType<?>,
            ? super IntegerType<?>,
            ? super IntegerType<?>,
            ? super IntegerType<?>> createPixelGapOperator() {
        return (querySignal, queryPix, targetGrad, targetDilated, gapPixel) -> {
            RGBPixelType<?> rgbQuery = (RGBPixelType<?>) queryPix;
            RGBPixelType<?> rgbTarget = (RGBPixelType<?>) targetDilated;
            if (rgbQuery.isNotZero() && rgbTarget.isNotZero()) {
                int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(
                        rgbQuery.getRed(), rgbQuery.getGreen(), rgbQuery.getBlue(),
                        rgbTarget.getRed(), rgbTarget.getGreen(), rgbTarget.getBlue()
                );
                if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                    gapPixel.setInteger(applyGapThreshold(pxGapSlice - DEFAULT_COLOR_FLUX));
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

    private final RandomAccessibleInterval<? extends RGBPixelType<?>> queryImageAccess;
    private final RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask;
    private final RandomAccessibleInterval<UnsignedByteType> querySignalAccess;
    private final RandomAccessibleInterval<UnsignedByteType> overexpressedQueryRegionsAccess;
    private final BiPredicate<long[], ? extends IntegerType<?>> excludedRegionCondition;
    private final int negativeRadius;

    Shape2DMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> sourceQueryImage,
                                          RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
                                          BiPredicate<long[], ? extends IntegerType<?>> excludedRegionCondition,
                                          int queryThreshold,
                                          int targetThreshold,
                                          boolean withMirrorFlag,
                                          int negativeRadius) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<? extends RGBPixelType<?>> maskedQueryImage =
                (RandomAccessibleInterval<? extends RGBPixelType<?>>) applyMaskCond(
                        (RandomAccessibleInterval<? extends IntegerType<?>>) sourceQueryImage,
                        (BiPredicate<long[], ? extends RGBPixelType<?>>) excludedRegionCondition);
        this.queryImageAccess = applyRGBThreshold(maskedQueryImage, queryThreshold);
        this.excludedRegionCondition = excludedRegionCondition;
        this.queryROIMask = queryROIMask;
        this.negativeRadius = negativeRadius;
        this.querySignalAccess = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.rgbToSignalTransformation(maskedQueryImage, 2),
                null,
                new UnsignedByteType(0)
        );
        this.overexpressedQueryRegionsAccess = createMaskForOverExpressedRegions(queryImageAccess);
    }

    @Override
    public RandomAccessibleInterval<? extends RGBPixelType<?>> getQueryImage() {
        return queryImageAccess;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.GradientImage, ComputeFileType.ZGapImage);
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> sourceTargetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        RandomAccessibleInterval<? extends IntegerType<?>> targetGradientImage = getVariantImage(
                targetVariantsSuppliers.get(ComputeFileType.GradientImage),
                () -> DistanceTransformAlgorithm.generateDistanceTransform(sourceTargetImage, negativeRadius)
        );
        if (targetGradientImage == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<? extends RGBPixelType<?>> maskedTargetImage =
                (RandomAccessibleInterval<? extends RGBPixelType<?>>) applyMaskCond(
                        (RandomAccessibleInterval<? extends IntegerType<?>>) sourceTargetImage,
                        (BiPredicate<long[], IntegerType<?>>) excludedRegionCondition);

        RandomAccessibleInterval<? extends RGBPixelType<?>> thresholdedTarget = applyRGBThreshold(
                maskedTargetImage,
                targetThreshold
        );
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<? extends RGBPixelType<?>> targetZGapMaskImage = (RandomAccessibleInterval<? extends RGBPixelType<?>>) getVariantImage(
                targetVariantsSuppliers.get(ComputeFileType.ZGapImage),
                () -> getDilation(thresholdedTarget, negativeRadius)
        );
        ShapeMatchScore shapeScore = calculateNegativeScores(
                queryImageAccess,
                querySignalAccess,
                overexpressedQueryRegionsAccess,
                maskedTargetImage,
                targetGradientImage,
                targetZGapMaskImage,
                false);

        if (withMirrorFlag) {
            @SuppressWarnings("unchecked")
            ShapeMatchScore mirroredShapedScore = calculateNegativeScores(
                    (RandomAccessibleInterval<? extends RGBPixelType<?>>) applyMask(
                            applyTransformToImage(queryImageAccess, new MirrorTransform(queryImageAccess.dimensionsAsLongArray(), 0)),
                            queryROIMask),
                    applyMask(
                            ImageTransforms.mirrorImage(querySignalAccess, 0),
                            queryROIMask),
                    applyMask(
                            ImageTransforms.mirrorImage(overexpressedQueryRegionsAccess, 0),
                            queryROIMask),
                    maskedTargetImage,
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

    private ShapeMatchScore calculateNegativeScores(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                    RandomAccessibleInterval<? extends IntegerType<?>> querySignalImage,
                                                    RandomAccessibleInterval<? extends IntegerType<?>> overexpressedQueryRegions,
                                                    RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage,
                                                    RandomAccessibleInterval<? extends IntegerType<?>> targetGradientImage,
                                                    RandomAccessibleInterval<? extends RGBPixelType<?>> targetZGapMaskImage,
                                                    boolean mirroredMask) {
        long startTime = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<UnsignedShortType> gapsImage = (RandomAccessibleInterval<UnsignedShortType>) applyQuadOp(
                querySignalImage,
                (RandomAccessibleInterval<? extends IntegerType<?>>) queryImage,
                targetGradientImage,
                (RandomAccessibleInterval<? extends IntegerType<?>>) targetZGapMaskImage,
                createPixelGapOperator(),
                new UnsignedShortType()
        );
        RandomAccessibleInterval<UnsignedByteType> overexpressedTargetRegions = applyBinaryOp(
                targetImage,
                overexpressedQueryRegions,
                (IntegerType<?> p1, IntegerType<?> p2, IntegerType<?> r) -> {
                    if (p2.getInteger() > 0) {
                        RGBPixelType<?> rgb1 = (RGBPixelType<?>) p1;
                        int r1 = rgb1.getRed();
                        int g1 = rgb1.getGreen();
                        int b1 = rgb1.getBlue();
                        // if any channel is > threshold, mark the pixel as signal
                        if (r1 > targetThreshold || g1 > targetThreshold || b1 > targetThreshold) {
                            r.setOne();
                            return;
                        }
                    }
                    r.setZero();
                },
                new UnsignedByteType(0)
        );
        long gradientAreaGap = ImageAccessUtils.fold(gapsImage,
                0L, (a, p) -> a + p.getInteger(), Long::sum);
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = ImageAccessUtils.fold(overexpressedTargetRegions,
                0L, (a, p) -> a + p.getInteger(), Long::sum);
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, mirroredMask);
    }

    private RandomAccessibleInterval<UnsignedByteType> createMaskForOverExpressedRegions(RandomAccessibleInterval<? extends RGBPixelType<?>> img) {
        // create a 60 px dilation and a 20px dilation
        // if the 20px dilation is 0 where the 60px dilation isn't then this is an overexpressed region (mark it as 1)
        RandomAccessibleInterval<? extends RGBPixelType<?>> candidateRegionsForHighExpression = createMaskForPotentialRegionsWithHighExpression(img, 60, 20);
        return rgb2Signal(candidateRegionsForHighExpression, 0);
    }

}
