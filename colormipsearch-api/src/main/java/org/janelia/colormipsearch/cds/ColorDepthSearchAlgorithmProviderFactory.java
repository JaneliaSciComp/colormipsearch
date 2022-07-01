package org.janelia.colormipsearch.cds;

import org.janelia.colormipsearch.imageprocessing.ColorTransformation;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageProcessing;
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
     * @param xyShift             - x-y translation for searching for a match
     * @return a color depth search search provider
     */
    public static ColorDepthSearchAlgorithmProvider<PixelMatchScore> createPixMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            double pixColorFluctuation,
            int xyShift,
            ImageRegionDefinition ignoredRegionsProvider) {
        LOG.info("Create mask comparator with mirrorQuery={}, dataThreshold={}, pixColorFluctuation={}, xyShift={}",
                mirrorMask, targetThreshold, pixColorFluctuation, xyShift);
        return new ColorDepthSearchAlgorithmProvider<PixelMatchScore>() {
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
            public ColorDepthSearchAlgorithm<PixelMatchScore> createColorDepthSearchAlgorithm(ImageArray<?> queryImageArray,
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

    public static ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShapeMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int negativeRadius,
            int borderSize,
            ImageArray<?> roiMaskImageArray,
            ImageRegionDefinition excludedRegions) {
        if (negativeRadius <= 0) {
            throw new IllegalArgumentException("The value for negative radius must be a positive integer - current value is " + negativeRadius);
        }
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("negativeRadius", negativeRadius)
                    .setParam("borderSize", borderSize);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray<?> queryImageArray,
                                                                                              int queryThreshold,
                                                                                              int queryBorderSize,
                                                                                              ColorDepthSearchParams cdsParams) {
                ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(excludedRegions.getRegion(queryImageArray));
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
                ShapeMatchColorDepthSearchAlgorithm maskNegativeScoresCalculator = new ShapeMatchColorDepthSearchAlgorithm(
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

}
