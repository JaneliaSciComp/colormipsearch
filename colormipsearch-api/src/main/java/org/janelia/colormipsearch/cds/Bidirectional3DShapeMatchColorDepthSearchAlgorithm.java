package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.QuadConverter;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm<P extends RGBPixelType<P>, G extends IntegerType<G>> implements ColorDepthSearchAlgorithm<ShapeMatchScore, P, G> {

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

    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithm.class);
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

    private final ImageAccess<P> queryImageAccess;
    private final ImageAccess<P> query3DSegmentation;
    private final ImageAccess<?> queryROIMask;
    private final ImageAccess<UnsignedByteType> querySignalAccess;
    private final int targetThreshold;
    private final boolean mirrorQuery;
    private final int negativeRadius;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(ImageAccess<P> queryImage,
                                                       ImageAccess<P> query3DSegmentation,
                                                       ImageAccess<?> queryROIMask,
                                                       int queryThreshold,
                                                       int targetThreshold,
                                                       boolean mirrorQuery,
                                                       int negativeRadius) {
        this.queryImageAccess = ImageTransforms.maskPixelsBelowThreshold(queryImage, queryThreshold);
        this.query3DSegmentation = query3DSegmentation;
        this.queryROIMask = queryROIMask;
        this.targetThreshold = targetThreshold;
        this.mirrorQuery = mirrorQuery;
        this.negativeRadius = negativeRadius;
        this.querySignalAccess = ImageTransforms.createRGBToSignalTransformation(queryImage, 2);
    }

    @Override
    public ImageAccess<P> getQueryImage() {
        return queryImageAccess;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetRGBVariantTypes() {
        return EnumSet.of(ComputeFileType.ZGapImage, ComputeFileType.SkeletonSWC, ComputeFileType.Vol3DSegmentation);
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
                                                  Map<ComputeFileType, Supplier<ImageAccess<G>>> targetGrayVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageAccess<G> targetGradientImage = getVariantImage(
                targetGrayVariantsSuppliers.get(ComputeFileType.GradientImage),
                null
        );
        if (targetGradientImage == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ShapeMatchScore shapeScore = new ShapeMatchScore(-1, -1, -1, mirrorQuery);
        // !!! TOD

        return shapeScore;
    }

    private <T extends Type<T>> ImageAccess<T> getVariantImage(Supplier<ImageAccess<T>> variantImageSupplier,
                                                               ImageAccess<T> defaultImageAccess) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return defaultImageAccess;
        }
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

}
