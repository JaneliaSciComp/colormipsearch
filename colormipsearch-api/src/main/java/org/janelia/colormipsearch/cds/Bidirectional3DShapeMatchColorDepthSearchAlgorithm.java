package org.janelia.colormipsearch.cds;

import java.awt.Image;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.Gray8ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.imageprocessing.IntQuadOperator;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bidirectional 3D shape match algorithm.
 * Computes gradient-based gap scores in both query->target and target->query directions
 * and returns the average as the final score.
 *
 * <p>Direction 1 (Query->Target): uses the query's gradient image and the target's signal.
 * <p>Direction 2 (Target->Query): uses the target's gradient image (from dilated segmented CDM)
 * and the query's signal.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm implements ColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithm.class);
    private static final int GAP_THRESHOLD = 3;
    private static final int TARGET_DILATION_RADIUS = 10;
    private static final int QUERY_DT_DILATION_RADIUS = 5;

    private final ImageArray queryImageArray;
    private final int queryThreshold;
    private final boolean withQueryMirroring;

    // Precomputed query-side images
    private final ImageArray queryGradient;    // distance transform of query CDM
    private final ImageArray queryBinaryMask;  // binary signal: 1 if any channel > threshold

    private final BiConsumer<ImageArray, String> callback;
    private final VolumeSegmentationHelper volumeSegmentationHelper;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
            ImageArray queryImageArray,
            Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers,
            int queryThreshold,
            boolean withQueryMirroring,
            String alignmentSpace,
            BiConsumer<ImageArray, String> callback) {
        this.queryImageArray = queryImageArray;
        callback.accept(queryImageArray, "cg query image");
        this.queryGradient = DistanceTransformAlgorithm.generateDistanceTransform(
                queryImageArray, QUERY_DT_DILATION_RADIUS, 1
        );
        callback.accept(queryGradient, "cg query gradient");
        this.queryBinaryMask = ImageOperations.duplicateImage(
                ImageOperations.binaryMask(
                        ImageOperations.rgbToGray8(this.queryImageArray),
                        queryThreshold,
                        1
                ),
                Gray8ImageArray::new
        );
        callback.accept(ImageOperations.binaryMask(queryBinaryMask, 0, 255), "cg query binary mask");
        this.queryThreshold = queryThreshold;
        this.withQueryMirroring = withQueryMirroring;


        // Initialize volume segmentation helper using the first available query variant
        this.volumeSegmentationHelper = new VolumeSegmentationHelper(
                alignmentSpace, queryVariantsSuppliers, callback
        );

        this.callback = callback;
    }

    @Override
    public ImageArray getQueryImage() {
        return queryImageArray;
    }

    @Override
    public int getQuerySize() {
        ImageArray ia = this.queryImageArray;
        int s = 0;
        for (int pi = 0; pi < ia.getSpatialSize(); pi++) {
            int pix = ia.getPackedIntValAtIndex(pi);
            int red = (pix >> 16) & 0xff;
            int green = (pix >> 8) & 0xff;
            int blue = pix & 0xff;
            if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                s++;
            }
        }
        return s;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.SkeletonSWC, ComputeFileType.Vol3DSegmentation);
    }

    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageArray targetImageArray,
                                                  Map<ComputeFileType, ComputeVariantImageSupplier> variantImageSuppliers) {
        long startTime = System.currentTimeMillis();

        if (!volumeSegmentationHelper.isAvailable()) {
            LOG.info("Bidirectional score cannot be computed - query 3D volume is not available");
            return new ShapeMatchScore(-1);
        }
        // Get the target 3D volume
        ImageArray target3DVolume = getTarget3DVolume(variantImageSuppliers);
        if (target3DVolume == null) {
            LOG.info("No target 3D volume provided");
            return new ShapeMatchScore(-1);
        }
        // Generate segmented CDM from target volume intersected with query volume
        ImageArray targetSegmentedCDM = volumeSegmentationHelper.generateSegmentedCDM(target3DVolume);
        if (targetSegmentedCDM == null) {
            return new ShapeMatchScore(-1);
        }
        callback.accept(targetSegmentedCDM, "cg target segmented CDM");

        // --- Direction 1: Query -> Target ---

        // Convert target CDM to binary mask
        ImageArray targetBinaryMask = ImageOperations.binaryMask(
                        ImageOperations.rgbToGray8(targetSegmentedCDM),
                        1,
                        1
                );

        callback.accept(ImageOperations.binaryMask(targetBinaryMask, 0, 255), "cg target binary mask");

        // gap = targetBinaryMask * queryGradient (where gap > GAP_THRESHOLD)
        long queryToTargetGap = computeGradientAreaGap(targetBinaryMask, queryGradient);
        LOG.debug("Query to target gradient area gap: {}", queryToTargetGap);

        // --- Direction 2: Target -> Query ---
        // Dilate target CDM (max filter radius 10)
        ImageArray dilatedTargetCDM = MaxFilterAlgorithm.maxRGBFilter2D(
                targetSegmentedCDM, TARGET_DILATION_RADIUS
        );
        callback.accept(dilatedTargetCDM, "cg 10px target dilation");

        // Distance transform of dilated target (no additional dilation)
        ImageArray targetGradient = DistanceTransformAlgorithm.generateDistanceTransformWithoutDilation(
                dilatedTargetCDM, 1
        );
        callback.accept(targetGradient, "cg target gradient");

        // gap = querySignal * targetGradient (where gap > GAP_THRESHOLD)
        long targetToQueryGap = computeGradientAreaGap(queryBinaryMask, targetGradient);
        LOG.debug("Target to query gradient area gap: {}", targetToQueryGap);

        // Final score: average of both directions
        long score = (queryToTargetGap + targetToQueryGap) / 2;
        long endTime = System.currentTimeMillis();
        LOG.debug("Final bidirectional score: {} - computed in {} secs",
                score, (endTime - startTime) / 1000.);

        return new ShapeMatchScore(score);
    }

    /**
     * Compute gradient area gap: sum of (signal * gradient) for all pixels where the product exceeds GAP_THRESHOLD.
     */
    private static long computeGradientAreaGap(ImageArray signal, ImageArray gradient) {
        long gap = 0;
        int size = signal.getSpatialSize();
        for (int pi = 0; pi < size; pi++) {
            int signalVal = signal.getPackedIntValAtIndex(pi);
            int gradVal = gradient.getPackedIntValAtIndex(pi);
            int gapVal = signalVal * gradVal;
            if (gapVal > GAP_THRESHOLD) {
                gap += gapVal;
            }
        }
        return gap;
    }

    /**
     * Get the target 3D volume (SWC or Vol3DSegmentation) from variant suppliers.
     */
    private ImageArray getTarget3DVolume(Map<ComputeFileType, ComputeVariantImageSupplier> variantImageSuppliers) {
        ComputeVariantImageSupplier vol3DSupplier = VolumeSegmentationHelper.get3DVolumeVariant(variantImageSuppliers);
        if (vol3DSupplier == null) {
            return null;
        }
        return vol3DSupplier.getImage();
    }
}
