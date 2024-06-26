package org.janelia.colormipsearch.api_v2.cdsearch;

import java.util.HashSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 * @see org.janelia.colormipsearch.cds.ShapeMatchColorDepthSearchAlgorithm
 */
@Deprecated
public class GradientBasedNegativeScoreColorDepthSearchAlgorithm implements ColorDepthSearchAlgorithm<NegativeColorDepthMatchScore> {

    private static final Logger LOG = LoggerFactory.getLogger(GradientBasedNegativeScoreColorDepthSearchAlgorithm.class);
    private static final Set<String> REQUIRED_VARIANT_TYPES = new HashSet<String>() {{
        add("gradient");
        add("zgap");
    }};
    private static final int DEFAULT_COLOR_FLUX = 40; // 40um
    private static final int GAP_THRESHOLD = 3;

    private static final TriFunction<Integer, Integer, Integer, Integer> PIXEL_GAP_OP = (gradScorePix, maskPix, dilatedPix) -> {
        if ((maskPix & 0xFFFFFF) != 0 && (dilatedPix & 0xFFFFFF) != 0) {
            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(maskPix, dilatedPix);
            if (DEFAULT_COLOR_FLUX <= pxGapSlice - DEFAULT_COLOR_FLUX) {
                // negative score value
                return pxGapSlice - DEFAULT_COLOR_FLUX;
            }
        }
        return gradScorePix;
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

    GradientBasedNegativeScoreColorDepthSearchAlgorithm(LImage queryImage,
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
        gapOp = (p1, p2, p3, p4) -> PIXEL_GAP_OP.apply(p1 * p2, p3, p4);
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
    public Set<String> getRequiredTargetVariantTypes() {
        return REQUIRED_VARIANT_TYPES;
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     * The gradient image must be non-null but the z-gap image can be null in which case it is calculated using
     * a dilation transformation.
     *
     * @param targetImageArray
     * @param variantTypeSuppliers
     * @return
     */
    @Override
    public NegativeColorDepthMatchScore calculateMatchingScore(@Nonnull ImageArray<?> targetImageArray,
                                                               Map<String, Supplier<ImageArray<?>>> variantTypeSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageArray<?> targetGradientImageArray = getVariantImageArray(variantTypeSuppliers, "gradient");
        if (targetGradientImageArray == null) {
            return new NegativeColorDepthMatchScore(-1, -1, false);
        }
        ImageArray<?> targetZGapMaskImageArray = getVariantImageArray(variantTypeSuppliers, "zgap");
        LImage targetImage = LImageUtils.create(targetImageArray).mapi(clearLabels);
        LImage targetGradientImage = LImageUtils.create(targetGradientImageArray);
        LImage targetZGapMaskImage = targetZGapMaskImageArray != null
                ? LImageUtils.create(targetZGapMaskImageArray)
                : negativeRadiusDilation.applyTo(targetImage.map(ColorTransformation.mask(queryThreshold)));

        NegativeColorDepthMatchScore negativeScores = calculateNegativeScores(targetImage, targetGradientImage, targetZGapMaskImage, ImageTransformation.IDENTITY, false);

        if (mirrorQuery) {
            LOG.trace("Start calculating area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            NegativeColorDepthMatchScore mirrorNegativeScores = calculateNegativeScores(targetImage, targetGradientImage, targetZGapMaskImage, ImageTransformation.horizontalMirror(), true);
            LOG.trace("Completed area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            if (mirrorNegativeScores.getScore() < negativeScores.getScore()) {
                return mirrorNegativeScores;
            }
        }
        return negativeScores;
    }

    private ImageArray<?> getVariantImageArray(Map<String, Supplier<ImageArray<?>>> variantTypeSuppliers, String variantType) {
        Supplier<ImageArray<?>> variantImageArraySupplier = variantTypeSuppliers.get(variantType);
        if (variantImageArraySupplier != null) {
            return variantImageArraySupplier.get();
        } else {
            return null;
        }
    }

    private NegativeColorDepthMatchScore calculateNegativeScores(LImage targetImage, LImage targetGradientImage, LImage targetZGapMaskImage, ImageTransformation maskTransformation, boolean useMirroredMask) {
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
        return new NegativeColorDepthMatchScore(gradientAreaGap, highExpressionArea, useMirroredMask);
    }

}
