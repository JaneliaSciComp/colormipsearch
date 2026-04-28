package org.janelia.colormipsearch.cds;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.ByteImageArray;
import org.janelia.colormipsearch.image.ShortImageArray;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.ImageArray;
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
    private final ShortImageArray queryGradient;       // distance transform of query CDM
    private final ByteImageArray querySignal;          // binary signal: 1 if any channel > threshold

    private final VolumeSegmentationHelper volumeSegmentationHelper;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
            ImageArray queryImageArray,
            Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers,
            int queryThreshold,
            boolean withQueryMirroring,
            String alignmentSpace) {
        this.queryImageArray = queryImageArray;
        this.queryThreshold = queryThreshold;
        this.withQueryMirroring = withQueryMirroring;

        // Convert the 2D query CDM to a ByteImageArray for gradient computation
        ByteImageArray queryForGradient = toByteImageArray(queryImageArray);

        // Compute query gradient (distance transform with dilation)
        this.queryGradient = DistanceTransformAlgorithm.generateDistanceTransform(
                queryForGradient, QUERY_DT_DILATION_RADIUS
        );

        // Compute query signal: binary image where pixel = 1 if any channel > threshold
        this.querySignal = computeSignal(queryForGradient, queryThreshold);

        // Initialize volume segmentation helper using the first available query variant
        this.volumeSegmentationHelper = new VolumeSegmentationHelper(
                alignmentSpace, queryVariantsSuppliers
        );
    }

    @Override
    public ImageArray getQueryImage() {
        return queryImageArray;
    }

    @Override
    public int getQuerySize() {
        ImageArray ia = this.queryImageArray;
        int s = 0;
        for (int pi = 0; pi < ia.getPixelCount(); pi++) {
            int pix = ia.get(pi);
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
    public int getQueryFirstPixelIndex() {
        ImageArray ia = this.queryImageArray;
        for (int pi = 0; pi < ia.getPixelCount(); pi++) {
            int pix = ia.get(pi);
            int red = (pix >> 16) & 0xff;
            int green = (pix >> 8) & 0xff;
            int blue = pix & 0xff;
            if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                return pi;
            }
        }
        return -1;
    }

    @Override
    public int getQueryLastPixelIndex() {
        ImageArray ia = this.queryImageArray;
        int last = -1;
        for (int pi = 0; pi < ia.getPixelCount(); pi++) {
            int pix = ia.get(pi);
            int red = (pix >> 16) & 0xff;
            int green = (pix >> 8) & 0xff;
            int blue = pix & 0xff;
            if (red > queryThreshold || green > queryThreshold || blue > queryThreshold) {
                last = pi;
            }
        }
        return last;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.SkeletonSWC, ComputeFileType.Vol3DSegmentation);
    }

    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageArray targetImageArray,
                                                  Map<ComputeFileType, Supplier<ImageArray>> variantImageSuppliers) {
        long startTime = System.currentTimeMillis();

        if (!volumeSegmentationHelper.isAvailable()) {
            LOG.info("Bidirectional score cannot be computed - query 3D volume is not available");
            return new ShapeMatchScore(-1);
        }

        // Get the target 3D volume
        org.janelia.colormipsearch.image.ImageArray target3DVolume = getTarget3DVolume(variantImageSuppliers);
        if (target3DVolume == null) {
            LOG.info("No target 3D volume provided");
            return new ShapeMatchScore(-1);
        }

        // Generate segmented CDM from target volume intersected with query volume
        ByteImageArray targetSegmentedCDM = volumeSegmentationHelper.generateSegmentedCDM(target3DVolume);
        if (targetSegmentedCDM == null) {
            return new ShapeMatchScore(-1);
        }

        // --- Direction 1: Query -> Target ---
        // Convert target CDM to binary signal
        ByteImageArray targetSignal = computeSignal(targetSegmentedCDM, 1);

        // gap = targetSignal * queryGradient (where gap > GAP_THRESHOLD)
        long queryToTargetGap = computeGradientAreaGap(targetSignal, queryGradient);
        LOG.debug("Query to target gradient area gap: {}", queryToTargetGap);

        // --- Direction 2: Target -> Query ---
        // Dilate target CDM (max filter radius 10)
        ByteImageArray dilatedTargetCDM = (ByteImageArray) MaxFilterAlgorithm.rgbMaxFilter2D(
                targetSegmentedCDM, TARGET_DILATION_RADIUS
        );

        // Distance transform of dilated target (no additional dilation)
        ShortImageArray targetGradient = DistanceTransformAlgorithm.generateDistanceTransformWithoutDilation(
                dilatedTargetCDM
        );

        // gap = querySignal * targetGradient (where gap > GAP_THRESHOLD)
        long targetToQueryGap = computeGradientAreaGap(querySignal, targetGradient);
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
    private static long computeGradientAreaGap(ByteImageArray signal, ShortImageArray gradient) {
        long gap = 0;
        int size = signal.getSpatialSize();
        for (int pi = 0; pi < size; pi++) {
            int signalVal = signal.getIntVal(pi);
            int gradVal = gradient.getIntVal(pi);
            int gapVal = signalVal * gradVal;
            if (gapVal > GAP_THRESHOLD) {
                gap += gapVal;
            }
        }
        return gap;
    }

    /**
     * Compute a binary signal image: pixel = 1 if any RGB channel > threshold, 0 otherwise.
     */
    private static ByteImageArray computeSignal(ByteImageArray rgbImage, int threshold) {
        int width = rgbImage.getWidth();
        int height = rgbImage.getHeight();
        ByteImageArray signal = new ByteImageArray(width, height, 1, 1);
        for (int pi = 0; pi < width * height; pi++) {
            int rgb = rgbImage.getIntVal(pi);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            if (r > threshold || g > threshold || b > threshold) {
                signal.setIntVal(pi, 1);
            }
        }
        return signal;
    }

    /**
     * Ensure the image is a ByteImageArray with 3 channels (RGB).
     * If it already is, cast directly; otherwise convert.
     */
    private static ByteImageArray toByteImageArray(ImageArray img) {
        if (img instanceof ByteImageArray && img.getChannels() == 3) {
            return (ByteImageArray) img;
        }
        int width = img.getWidth();
        int height = img.getHeight();
        ByteImageArray newImg = new ByteImageArray(width, height, 1, 3);
        for (int pi = 0; pi < width * height; pi++) {
            newImg.setIntVal(pi, img.get(pi));
        }
        return newImg;
    }

    /**
     * Get the target 3D volume from variant suppliers.
     * Tries Vol3DSegmentation first, then SkeletonSWC.
     */
    private ImageArray getTarget3DVolume(
            Map<ComputeFileType, Supplier<ImageArray>> variantImageSuppliers) {
        Supplier<ImageArray> vol3DSupplier = variantImageSuppliers.get(ComputeFileType.Vol3DSegmentation);
        if (vol3DSupplier == null) {
            vol3DSupplier = variantImageSuppliers.get(ComputeFileType.SkeletonSWC);
        }
        if (vol3DSupplier == null) {
            return null;
        }
        return vol3DSupplier.get();
    }
}
