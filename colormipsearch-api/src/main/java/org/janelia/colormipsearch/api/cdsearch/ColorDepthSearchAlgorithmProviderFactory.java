package org.janelia.colormipsearch.api.cdsearch;

import java.io.Serializable;

import org.janelia.colormipsearch.api.imageprocessing.ColorTransformation;
import org.janelia.colormipsearch.api.imageprocessing.ImageArray;
import org.janelia.colormipsearch.api.imageprocessing.ImageProcessing;
import org.janelia.colormipsearch.api.imageprocessing.ImageTransformation;
import org.janelia.colormipsearch.api.imageprocessing.LImage;
import org.janelia.colormipsearch.api.imageprocessing.LImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for a color depth search comparator.
 */
public class ColorDepthSearchAlgorithmProviderFactory implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ColorDepthSearchAlgorithmProviderFactory.class);

    /**
     * Create a color depth query searcher that calculates only positive scores.
     *
     * @param mirrorMask flag whether to use mirroring
     * @param targetThreshold data threshold
     * @param pixColorFluctuation z - gap tolerance - sometimes called pixel color fluctuation
     * @param xyShift - x-y translation for searching for a match
     * @return a color depth search search provider
     */
    public static ColorDepthSearchAlgorithmProvider<ColorMIPMatchScore> createPixMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            double pixColorFluctuation,
            int xyShift,
            ImageRegionGenerator ignoredRegionsProvider) {
        LOG.info("Create mask comparator with mirrorQuery={}, dataThreshold={}, pixColorFluctuation={}, xyShift={}",
                mirrorMask, targetThreshold, pixColorFluctuation, xyShift);
        return new ColorDepthSearchAlgorithmProvider<ColorMIPMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("dataThreshold", targetThreshold)
                    .setParam("pixColorFluctuation", pixColorFluctuation)
                    .setParam("xyShift", xyShift);
            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ColorMIPMatchScore> createColorDepthQuerySearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                                      int queryThreshold,
                                                                                                      int queryBorderSize,
                                                                                                      ColorDepthSearchParams cdsParams) {
                Double pixColorFluctuationParam = cdsParams.getDoubleParam("pixColorFluctuation", pixColorFluctuation);
                double zTolerance = pixColorFluctuationParam == null ? 0. : pixColorFluctuationParam / 100;
                return new PixelMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryThreshold,
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        null,
                        0,
                        false,
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        zTolerance,
                        cdsParams.getIntParam("xyShift", xyShift),
                        ignoredRegionsProvider);
            }
        };
    }

    public static ColorDepthSearchAlgorithmProvider<NegativeColorDepthMatchScore> createNegativeMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int negativeRadius,
            int borderSize,
            ImageArray<?> roiMaskImageArray,
            ImageRegionGenerator ignoredRegionsProvider) {
        if (negativeRadius <= 0) {
            throw new IllegalArgumentException("The value for negative radius must be a positive integer - current value is " + negativeRadius);
        }
        return new ColorDepthSearchAlgorithmProvider<NegativeColorDepthMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("negativeRadius", negativeRadius)
                    .setParam("borderSize", borderSize);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<NegativeColorDepthMatchScore> createColorDepthQuerySearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                                                int queryThreshold,
                                                                                                                int queryBorderSize,
                                                                                                                ColorDepthSearchParams cdsParams) {
                ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(ignoredRegionsProvider.getRegion(queryImageArray));
                ImageProcessing negativeRadiusDilation = ImageProcessing.create(clearIgnoredRegions)
                        .applyColorTransformation(ColorTransformation.mask(queryThreshold))
                        .unsafeMaxFilter(cdsParams.getIntParam("negativeRadius", negativeRadius));
                long startTime = System.currentTimeMillis();
                LImage roiMaskImage;
                if (roiMaskImageArray == null) {
                    roiMaskImage = null;
                } else {
                    roiMaskImage = LImageUtils.create(roiMaskImageArray).mapi(clearIgnoredRegions);
                }
                LImage queryImage = LImageUtils.create(queryImageArray, borderSize, borderSize, borderSize, borderSize).mapi(clearIgnoredRegions);

                LImage maskForRegionsWithTooMuchExpression = LImageUtils.combine2(
                        queryImage.mapi(ImageTransformation.unsafeMaxFilter(60)),
                        queryImage.mapi(ImageTransformation.unsafeMaxFilter(20)),
                        (p1, p2) -> {
                            return (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1;
                        } // mask pixels from the 60x image if they are present in the 20x image
                );
                GradientBasedNegativeScoreColorDepthSearchAlgorithm maskNegativeScoresCalculator = new GradientBasedNegativeScoreColorDepthSearchAlgorithm(
                        queryImage,
                        queryImage.map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(2)).reduce(),
                        maskForRegionsWithTooMuchExpression.map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(0)).reduce(),
                        roiMaskImage,
                        cdsParams.getIntParam("queryThreshold", queryThreshold),
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        clearIgnoredRegions,
                        negativeRadiusDilation
                );

                LOG.debug("Created gradient area gap calculator for mask in {}ms", System.currentTimeMillis() - startTime);
                return maskNegativeScoresCalculator;
            }
        };
    }

    public static ColorDepthSearchAlgorithmProvider<ColorMIPMatchScore> createPixMatchWithNegativeScoreCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            double pixColorFluctuation,
            int xyShift,
            int negativeRadius,
            ImageArray<?> roiMaskImageArray,
            ImageRegionGenerator ignoredRegionsProvider) {
        if (negativeRadius <= 0) {
            throw new IllegalArgumentException("The value for negative radius must be a positive integer - current value is " + negativeRadius);
        }
        return new ColorDepthSearchAlgorithmProvider<ColorMIPMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                        .setParam("mirrorMask", mirrorMask)
                        .setParam("dataThreshold", targetThreshold)
                        .setParam("pixColorFluctuation", pixColorFluctuation)
                        .setParam("xyShift", xyShift)
                        .setParam("negativeRadius", negativeRadius);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ColorMIPMatchScore> createColorDepthQuerySearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                                      int queryThreshold,
                                                                                                      int queryBorderSize,
                                                                                                      ColorDepthSearchParams cdsParams) {
                ColorDepthSearchAlgorithm<ColorMIPMatchScore> posScoreCDSAlg =
                        createPixMatchCDSAlgorithmProvider(mirrorMask, targetThreshold, pixColorFluctuation, xyShift, ignoredRegionsProvider)
                                .createColorDepthQuerySearchAlgorithm(queryImageArray, queryThreshold, queryBorderSize, cdsParams);
                ColorDepthSearchAlgorithm<NegativeColorDepthMatchScore> negScoreCDSAlg =
                        createNegativeMatchCDSAlgorithmProvider(mirrorMask, negativeRadius, queryBorderSize, roiMaskImageArray, ignoredRegionsProvider)
                                .createColorDepthQuerySearchAlgorithm(queryImageArray, queryThreshold, queryBorderSize, cdsParams);
                return new PixelMatchWithNegativeScoreColorDepthSearchAlgorithm(posScoreCDSAlg, negScoreCDSAlg);
            }
        };
    }

}
