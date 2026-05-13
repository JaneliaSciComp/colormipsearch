package org.janelia.colormipsearch.cds;

import java.util.Map;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.mips.ComputeVariantImageSupplier;
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
     * @param queryThreshold     query threshold
     * @param targetThreshold     data threshold
     * @param mirrorMask          flag whether to use mirroring
     * @param pixColorFluctuation z - gap tolerance - sometimes called pixel color fluctuation
     * @param xyShift             x-y translation when searching for a match - this is an even number
     *                            because a shift by 1 pixel is too small so we always shift by
     *                            multiples of 2 pixels
     * @return a pixelmatch color depth search algorithm provider
     */
    public static ColorDepthSearchAlgorithmProvider<PixelMatchScore> createPixMatchCDSAlgorithmProvider(
            int queryThreshold,
            int targetThreshold,
            boolean mirrorMask,
            double pixColorFluctuation,
            int xyShift,
            ImageMaskPredicate labelsMaskPredicate) {
        LOG.info("Create mask comparator with mirrorQuery={}, dataThreshold={}, pixColorFluctuation={}, xyShift={}",
                mirrorMask, targetThreshold, pixColorFluctuation, xyShift);
        return new ColorDepthSearchAlgorithmProvider<PixelMatchScore>() {
            @Override
            public ColorDepthSearchAlgorithm<PixelMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers) {
                double zTolerance = pixColorFluctuation / 100;
                if ((xyShift & 0x1) == 1) {
                    throw new IllegalArgumentException("XY shift parameter must be an even number.");
                }
                return new PixelMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryThreshold,
                        mirrorMask,
                        targetThreshold,
                        zTolerance,
                        xyShift,
                        labelsMaskPredicate);
            }
        };
    }

    public static ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShapeMatchCDSAlgorithmProvider(
            int queryThreshold,
            boolean mirrorMask,
            ImageArray roiMaskImageArray,
            ImageMaskPredicate labelsMaskPredicate) {
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers) {
                long startTime = System.currentTimeMillis();
                Shape2DMatchColorDepthSearchAlgorithm shapeScoresCalculator = new Shape2DMatchColorDepthSearchAlgorithm(
                        queryImageArray, // EM
                        roiMaskImageArray,
                        queryThreshold,
                        mirrorMask,
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
            int queryThreshold,
            boolean mirrorMask,
            ImageMaskPredicate labelsMaskPredicate) {
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(ImageArray queryImageArray,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers) {
                long startTime = System.currentTimeMillis();
                Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                        new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                                queryImageArray,
                                queryVariantsSuppliers,
                                queryThreshold,
                                mirrorMask,
                                alignmentSpace,
                                labelsMaskPredicate,
                                (imageArray, title) -> {}
                        );
                LOG.debug("Created bidirectional shape match calculator in {}ms",
                        System.currentTimeMillis() - startTime);
                return algorithm;
            }
        };
    }

}
