package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.imageprocessing.ColorTransformation;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageProcessing;
import org.janelia.colormipsearch.imageprocessing.ImageTransformation;
import org.janelia.colormipsearch.imageprocessing.LImage;
import org.janelia.colormipsearch.imageprocessing.LImageUtils;
import org.janelia.colormipsearch.imageprocessing.QuadFunction;
import org.janelia.colormipsearch.imageprocessing.TriFunction;
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

    private static final TriFunction<Integer, Integer, Integer, Integer> PIXEL_GAP_OP = (targetGradPix, queryPix, targetDilatedPix) -> {
        if ((queryPix & 0xFFFFFF) != 0 && (targetDilatedPix & 0xFFFFFF) != 0) {
            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(queryPix, targetDilatedPix);
            if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                // negative score value
                return pxGapSlice - DEFAULT_COLOR_FLUX;
            }
        }
        return targetGradPix; // this is conditioned actually by the querySignal
    };

    private final LImage queryImage;
    private final LImage queryIntensityValues;
    private final LImage queryHighExpressionMask; // pix(x,y) = 1 if there's too much expression surrounding x,y
    private final LImage queryROIMaskImage;
    private final int queryThreshold;
    private final boolean mirrorQuery;
    private final ImageTransformation clearLabels;
    private final ImageProcessing negativeRadiusDilation;
    private final QuadFunction<Integer, Integer, Integer, Integer, Integer> gapOp;

    ShapeMatchColorDepthSearchAlgorithm(LImage queryImage,
                                        LImage queryIntensityValues,
                                        LImage queryHighExpressionMask,
                                        LImage queryROIMaskImage,
                                        int queryThreshold,
                                        boolean mirrorQuery,
                                        ImageTransformation clearLabels,
                                        ImageProcessing negativeRadiusDilation) {
        this.queryImage = queryImage;
        this.queryIntensityValues = queryIntensityValues;
        this.queryHighExpressionMask = queryHighExpressionMask;
        this.queryROIMaskImage = queryROIMaskImage;
        this.queryThreshold = queryThreshold;
        this.mirrorQuery = mirrorQuery;
        this.clearLabels = clearLabels;
        this.negativeRadiusDilation = negativeRadiusDilation;
        this.gapOp = (/*querySignal*/p1,
                      /*targetGrad*/p2,
                      /*queryPix*/p3,
                      /*target20pxDilation*/p4) -> PIXEL_GAP_OP.apply(p1 * p2, p3, p4);
    }

    @Override
    public ImageArray<?> getQueryImage() {
        return queryImage.toImageArray();
    }

    @Override
    public int getQuerySize() {
        return queryImage.fold(0, (pix, s) -> {
            int red = (pix >> 16) & 0xff;
            int green = (pix >> 8) & 0xff;
            int blue = pix & 0xff;

            if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                return s + 1;
            } else {
                return s;
            }
        });
    }

    @Override
    public int getQueryFirstPixelIndex() {
        return findQueryFirstPixelIndex();
    }

    private int findQueryFirstPixelIndex() {
        return queryImage.foldi(-1, (x, y, pix, res) -> {
            if (res == -1) {
                int red = (pix >> 16) & 0xff;
                int green = (pix >> 8) & 0xff;
                int blue = pix & 0xff;

                if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                    return y * queryImage.width() + x;
                } else {
                    return res;
                }
            } else {
                return res;
            }
        });
    }

    @Override
    public int getQueryLastPixelIndex() {
        return findQueryLastPixelIndex();
    }

    private int findQueryLastPixelIndex() {
        return queryImage.foldi(-1, (x, y, pix, res) -> {
            int red = (pix >> 16) & 0xff;
            int green = (pix >> 8) & 0xff;
            int blue = pix & 0xff;

            if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                int index = y * queryImage.width() + x;
                if (index > res) {
                    return index;
                } else {
                    return res;
                }
            } else {
                return res;
            }
        });
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
     * @param targetImageArray
     * @param variantImageSuppliers
     * @return
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageArray<?> targetImageArray,
                                                  Map<ComputeFileType, Supplier<ImageArray<?>>> variantImageSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageArray<?> targetGradientImageArray = getVariantImageArray(variantImageSuppliers.get(ComputeFileType.GradientImage));
        if (targetGradientImageArray == null) {
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ImageArray<?> targetZGapMaskImageArray = getVariantImageArray(variantImageSuppliers.get(ComputeFileType.ZGapImage));
        LImage targetImage = LImageUtils.create(targetImageArray).mapi(clearLabels);
        LImage targetGradientImage = LImageUtils.create(targetGradientImageArray);
        LImage targetZGapMaskImage = targetZGapMaskImageArray != null
                ? LImageUtils.create(targetZGapMaskImageArray)
                : negativeRadiusDilation.applyTo(targetImage.map(ColorTransformation.mask(queryThreshold)));

        ShapeMatchScore negativeScores = calculateNegativeScores(targetImage, targetGradientImage, targetZGapMaskImage, ImageTransformation.IDENTITY, false);

        if (mirrorQuery) {
            LOG.trace("Start calculating area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            ShapeMatchScore mirrorNegativeScores = calculateNegativeScores(targetImage, targetGradientImage, targetZGapMaskImage, ImageTransformation.horizontalMirror(), true);
            LOG.trace("Completed area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            if (mirrorNegativeScores.getScore() < negativeScores.getScore()) {
                return mirrorNegativeScores;
            }
        }
        return negativeScores;
    }

    private ImageArray<?> getVariantImageArray(Supplier<ImageArray<?>> variantImageSupplier) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.get();
        } else {
            return null;
        }
    }

    private ShapeMatchScore calculateNegativeScores(LImage targetImage, LImage targetGradientImage, LImage targetZGapMaskImage, ImageTransformation maskTransformation, boolean useMirroredMask) {
        long startTime = System.currentTimeMillis();
        LImage queryROIImage;
        LImage queryIntensitiesROIImage;
        LImage queryHighExpressionMaskROIImage;
        if (queryROIMaskImage == null) {
            queryROIImage = queryImage.mapi(maskTransformation);
            queryIntensitiesROIImage = queryIntensityValues.mapi(maskTransformation);
            queryHighExpressionMaskROIImage = queryHighExpressionMask.mapi(maskTransformation);
        } else {
            queryROIImage = LImageUtils.combine2(
                    queryImage.mapi(maskTransformation),
                    queryROIMaskImage,
                    (p1, p2) -> ColorTransformation.mask(queryImage.getPixelType(), p1, p2));
            queryIntensitiesROIImage = LImageUtils.combine2(
                    queryIntensityValues.mapi(maskTransformation),
                    queryROIMaskImage,
                    (p1, p2) -> ColorTransformation.mask(queryIntensityValues.getPixelType(), p1, p2));
            queryHighExpressionMaskROIImage = LImageUtils.combine2(
                    queryHighExpressionMask.mapi(maskTransformation),
                    queryROIMaskImage,
                    (p1, p2) -> ColorTransformation.mask(queryHighExpressionMask.getPixelType(), p1, p2));
        }
        LImage gaps = LImageUtils.combine4(
                queryIntensitiesROIImage,
                targetGradientImage,
                queryROIImage,
                targetZGapMaskImage.mapi(maskTransformation),
                gapOp.andThen(gap -> gap > GAP_THRESHOLD ? gap : 0)
        );
        LImage highExpressionRegions = LImageUtils.combine2(
                targetImage,
                queryHighExpressionMaskROIImage,
                (p1, p2) -> {
                    if (p2 == 1) {
                        int r1 = (p1 >> 16) & 0xff;
                        int g1 = (p1 >> 8) & 0xff;
                        int b1 = p1 & 0xff;
                        if (r1 > queryThreshold || g1 > queryThreshold || b1 > queryThreshold) {
                            return 1;
                        }
                    }
                    return 0;
                });
        long gradientAreaGap = gaps.fold(0L, Long::sum);
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = highExpressionRegions.fold(0L, Long::sum);
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, useMirroredMask);
    }

}
