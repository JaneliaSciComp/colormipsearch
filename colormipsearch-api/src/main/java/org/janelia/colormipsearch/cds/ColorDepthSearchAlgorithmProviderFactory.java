package org.janelia.colormipsearch.cds;

import java.util.Map;
import java.util.function.Supplier;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.model.ComputeFileType;
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
            ImageMaskPredicate labelsMaskPredicate) {
        LOG.info("Create mask comparator with mirrorQuery={}, dataThreshold={}, pixColorFluctuation={}, xyShift={}",
                mirrorMask, targetThreshold, pixColorFluctuation, xyShiftParam);
        return new ColorDepthSearchAlgorithmProvider<PixelMatchScore>() {
            final ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("dataThreshold", targetThreshold)
                    .setParam("pixColorFluctuation", pixColorFluctuation)
                    .setParam("xyShift", xyShiftParam);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<PixelMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers,
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
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        zTolerance,
                        xyShift,
                        labelsMaskPredicate);
            }
        };
    }

    public static ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShapeMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            ImageArray roiMaskImageArray,
            ImageMaskPredicate labelsMaskPredicate) {
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            final ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    ;

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers,
                                                                                              int queryThreshold,
                                                                                              int queryBorderSize,
                                                                                              ColorDepthSearchParams cdsParams) {
                long startTime = System.currentTimeMillis();
                Shape2DMatchColorDepthSearchAlgorithm shapeScoresCalculator = new Shape2DMatchColorDepthSearchAlgorithm(
                        queryImageArray, // EM
                        roiMaskImageArray,
                        queryThreshold,
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        labelsMaskPredicate
                );
                LOG.debug("Created gradient area gap calculator for mask in {}ms", System.currentTimeMillis() - startTime);
                return shapeScoresCalculator;
            }
        };
    }

    /**
     * Create a provider for the bidirectional 3D shape match algorithm.
     *
     * @param alignmentSpace         alignment space name (e.g., "JRC2018_Unisex_20x_HR")
     * @param mirrorMask             flag whether to use mirroring
     * @return a color depth search algorithm provider for bidirectional shape matching
     */
    public static ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createBidirectionalShapeMatchCDSAlgorithmProvider(
            String alignmentSpace,
            boolean mirrorMask) {
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            final ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("alignmentSpace", alignmentSpace);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers,
                                                                                              int queryThreshold,
                                                                                              int queryBorderSize,
                                                                                              ColorDepthSearchParams cdsParams) {
                long startTime = System.currentTimeMillis();
                Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                        new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                                queryImageArray,
                                queryVariantsSuppliers,
                                queryThreshold,
                                cdsParams.getBoolParam("mirrorMask", mirrorMask),
                                cdsParams.getStringParam("alignmentSpace") != null
                                        ? cdsParams.getStringParam("alignmentSpace") : alignmentSpace
                        );
                LOG.debug("Created bidirectional shape match calculator in {}ms",
                        System.currentTimeMillis() - startTime);
                return algorithm;
            }
        };
    }

}
