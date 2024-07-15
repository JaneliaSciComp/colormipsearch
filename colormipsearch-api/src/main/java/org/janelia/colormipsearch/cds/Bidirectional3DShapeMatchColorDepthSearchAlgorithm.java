package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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

    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithm.class);
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

    private final RandomAccessibleInterval<P> queryImageAccess;
    private final RandomAccessibleInterval<P> query3DSegmentation;
    private final RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask;
    private final RandomAccessibleInterval<UnsignedByteType> querySignalAccess;
    private final int targetThreshold;
    private final boolean mirrorQuery;
    private final int negativeRadius;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<P> queryImage,
                                                       RandomAccessibleInterval<P> query3DSegmentation,
                                                       RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
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
    public RandomAccessibleInterval<P> getQueryImage() {
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
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<P> targetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<P>>> rgbVariantsSuppliers,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<G>>> targetGrayVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        RandomAccessibleInterval<G> targetGradientImage = getVariantImage(
                targetGrayVariantsSuppliers.get(ComputeFileType.GradientImage),
                null
        );
        if (targetGradientImage == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ShapeMatchScore shapeScore = new ShapeMatchScore(-1, -1, -1, mirrorQuery);
        long endTime = System.currentTimeMillis();
        // !!! TOD

        return shapeScore;
    }

    private <T extends Type<T>> RandomAccessibleInterval<T> getVariantImage(Supplier<RandomAccessibleInterval<T>> variantImageSupplier,
                                                                            RandomAccessibleInterval<T> defaultImageAccess) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return defaultImageAccess;
        }
    }

}
