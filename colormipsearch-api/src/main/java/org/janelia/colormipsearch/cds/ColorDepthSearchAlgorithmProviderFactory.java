package org.janelia.colormipsearch.cds;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
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
     * @param mirrorMask               flag whether to use mirroring
     * @param targetThreshold          data threshold
     * @param pixColorFluctuation      z - gap tolerance - sometimes called pixel color fluctuation
     * @param xyShiftParam             x-y translation when searching for a match - this is an even number
     *                                 because a shift by 1 pixel is too small so we always shift by
     *                                 multiples of 2 pixels
     * @param excludedRegionsCondition
     * @return a color depth search search provider
     */
    public static ColorDepthSearchAlgorithmProvider<PixelMatchScore> createPixMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            double pixColorFluctuation,
            int xyShiftParam,
            BiPredicate<long[], long[]> excludedRegionsCondition) {
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
            public ColorDepthSearchAlgorithm<PixelMatchScore> createColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers,
                                                                                              int queryThreshold,
                                                                                              ColorDepthSearchParams cdsParams) {
                Double pixColorFluctuationParam = cdsParams.getDoubleParam("pixColorFluctuation", pixColorFluctuation);
                double zTolerance = pixColorFluctuationParam == null ? 0. : pixColorFluctuationParam / 100;
                BiPredicate<long[], IntegerType<?>> insideExcludedRegion = (pos, pix) -> excludedRegionsCondition.test(pos, queryImage.dimensionsAsLongArray());
                return new PixelMatchColorDepthSearchAlgorithm(
                        queryImage,
                        insideExcludedRegion,
                        queryThreshold,
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        zTolerance,
                        cdsParams.getIntParam("xyShift", xyShiftParam)
                );
            }
        };
    }

    public static <P extends RGBPixelType<P>> ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShape2DMatchCDSAlgorithmProvider(
            boolean mirrorMask,
            int targetThreshold,
            int negativeRadius,
            BiPredicate<long[], long[]> excludedRegionsCondition,
            RandomAccessibleInterval<? extends IntegerType<?>> roiMask) {
        if (negativeRadius <= 0) {
            throw new IllegalArgumentException("The value for negative radius must be a positive integer - current value is " + negativeRadius);
        }
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            final ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("negativeRadius", negativeRadius)
                    .setParam("dataThreshold", targetThreshold);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers,
                                                                                              int queryThreshold,
                                                                                              ColorDepthSearchParams cdsParams) {
                BiPredicate<long[], P> insideExcludedRegion = (pos, pix) -> excludedRegionsCondition.test(pos, queryImage.dimensionsAsLongArray());
                return new Shape2DMatchColorDepthSearchAlgorithm(
                        queryImage,
                        roiMask,
                        insideExcludedRegion,
                        queryThreshold,
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        cdsParams.getBoolParam("mirrorMask", mirrorMask),
                        cdsParams.getIntParam("negativeRadius", negativeRadius)
                );
            }

        };
    }

    public static <P extends RGBPixelType<P>> ColorDepthSearchAlgorithmProvider<ShapeMatchScore> createShape3DBidirectionalMatchCDSAlgorithmProvider(
            String alignmentSpace,
            boolean mirrorMask,
            int targetThreshold,
            int negativeRadius,
            BiPredicate<long[], long[]> excludedRegionsCondition,
            RandomAccessibleInterval<? extends IntegerType<?>> roiMask,
            ExecutorService executorService) {
        if (negativeRadius <= 0) {
            throw new IllegalArgumentException("The value for negative radius must be a positive integer - current value is " + negativeRadius);
        }
        return new ColorDepthSearchAlgorithmProvider<ShapeMatchScore>() {
            final ColorDepthSearchParams defaultCDSParams = new ColorDepthSearchParams()
                    .setParam("mirrorMask", mirrorMask)
                    .setParam("negativeRadius", negativeRadius)
                    .setParam("dataThreshold", targetThreshold);

            @Override
            public ColorDepthSearchParams getDefaultCDSParams() {
                return defaultCDSParams;
            }

            @Override
            public ColorDepthSearchAlgorithm<ShapeMatchScore> createColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                                                              Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers,
                                                                                              int queryThreshold,
                                                                                              ColorDepthSearchParams cdsParams) {
                BiPredicate<long[], P> insideExcludedRegion = (pos, pix) -> excludedRegionsCondition.test(pos, queryImage.dimensionsAsLongArray());
                Bidirectional3DShapeMatchColorDepthSearchAlgorithm bidirectional3DShapeMatchColorDepthSearchAlgorithm = new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                        queryImage,
                        queryVariantsSuppliers,
                        insideExcludedRegion,
                        roiMask,
                        executorService,
                        alignmentSpace,
                        queryThreshold,
                        cdsParams.getIntParam("dataThreshold", targetThreshold),
                        cdsParams.getBoolParam("mirrorMask", mirrorMask)
                );
                if (bidirectional3DShapeMatchColorDepthSearchAlgorithm.isAvailable()) {
                    return bidirectional3DShapeMatchColorDepthSearchAlgorithm;
                } else {
                    // if the 3-D volumes are not available use 2-D shape scoring
                    return new Shape2DMatchColorDepthSearchAlgorithm(
                            queryImage,
                            roiMask,
                            insideExcludedRegion,
                            queryThreshold,
                            cdsParams.getIntParam("dataThreshold", targetThreshold),
                            cdsParams.getBoolParam("mirrorMask", mirrorMask),
                            cdsParams.getIntParam("negativeRadius", negativeRadius)
                    );
                }
            }

        };
    }

}
