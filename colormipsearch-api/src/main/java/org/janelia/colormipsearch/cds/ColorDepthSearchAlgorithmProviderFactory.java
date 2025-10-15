package org.janelia.colormipsearch.cds;

import org.janelia.colormipsearch.imageprocessing.ColorTransformation;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.imageprocessing.ImageTransformation;
import org.janelia.colormipsearch.imageprocessing.LImage;
import org.janelia.colormipsearch.imageprocessing.LImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for a color depth search comparator.
 */
public class ColorDepthSearchAlgorithmProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ColorDepthSearchAlgorithmProviderFactory.class);

    /**
     * Create a color depth query searcher that calculates only positive scores.
     *
     * @param mirrorMask          flag whether to use mirroring
     * @param targetThreshold     data threshold
     * @param pixColorFluctuation z - gap tolerance - sometimes called pixel color fluctuation
     * @param xyShiftParam        x-y translation when searching for a match - this is an even number
     *                            because a shift by 1 pixel is too small so we always shift by
     *                            multiples of 2 pixels
     * @return a color depth search search provider
     */
    public static ColorDepthSearchAlgorithmProvider<PixelMatchScore> createPixMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            double pixColorFluctuation,
            int xyShiftParam,
            ImageRegionDefinition ignoredRegionsProvider) {
        LOG.info("Create mask comparator with mirrorQuery={}, dataThreshold={}, pixColorFluctuation={}, xyShift={}",
                mirrorMask, targetThreshold, pixColorFluctuation, xyShiftParam);
        return new ColorDepthSearchAlgorithmProvider<PixelMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("dataThreshold", targetThreshold)
                    .setParam("pixColorFluctuation", pixColorFluctuation)
                    .setParam("xyShift", xyShiftParam);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<PixelMatchScore> createColorDepthSearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                              int queryThreshold,
                                                                                              int queryBorderSize,
                                                                                              ColorDepthSearchParams cdsParams) {
                Double pixColorFluctuationParam = cdsParams.getDoubleParam("pixColorFluctuation", pixColorFluctuation);
                double zTolerance = pixColorFluctuationParam == null ? 0. : pixColorFluctuationParam / 100;
                int xyShift = cdsParams.getIntParam("xyShift", xyShiftParam);
                if ((xyShift & 0x1) == 1) {
                    throw new IllegalArgumentException("XY shift parameter must be an even number.");
                }
                return new PixelMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryThreshold,
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        null,
                        0,
                        false,
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        zTolerance,
                        xyShift,
                        ignoredRegionsProvider);
            }
        };
    }

    public static ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShapeMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            ImageArray<?> roiMaskImageArray,
            ImageRegionDefinition excludedRegions) {
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    ;

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                              int queryThreshold,
                                                                                              int queryBorderSize,
                                                                                              ColorDepthSearchParams cdsParams) {
                long startTime = System.currentTimeMillis();
                ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(excludedRegions.getRegion(queryImageArray));
                LImage roiMaskImage;
                if (roiMaskImageArray == null) {
                    roiMaskImage = null;
                } else {
                    roiMaskImage = LImageUtils.create(roiMaskImageArray).mapi(clearIgnoredRegions);
                }
                LImage queryImage = LImageUtils.create(queryImageArray, queryBorderSize, queryBorderSize, queryBorderSize, queryBorderSize).mapi(clearIgnoredRegions);

                LImage maskForRegionsWithTooMuchExpression = LImageUtils.combine2(
                        queryImage.mapi(ImageTransformation.unsafeMaxFilter(60)),
                        queryImage.mapi(ImageTransformation.unsafeMaxFilter(20)),
                        (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1 // mask pixels from the 60x image if they are present in the 20x image
                ).map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(0)).reduce();

                LImage queryMask = queryImage.map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(2)).reduce();

                Shape2DMatchColorDepthSearchAlgorithm maskNegativeScoresCalculator = new Shape2DMatchColorDepthSearchAlgorithm(
                        queryImage, // EM
                        queryMask, // EM mask
                        maskForRegionsWithTooMuchExpression,
                        roiMaskImage,
                        queryThreshold,
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        clearIgnoredRegions
                );

                LOG.debug("Created gradient area gap calculator for mask in {}ms", System.currentTimeMillis() - startTime);
                return maskNegativeScoresCalculator;
            }
        };
    }

}
