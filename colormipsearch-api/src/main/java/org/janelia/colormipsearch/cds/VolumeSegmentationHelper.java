package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.algorithms.CDMGenerationAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Connect3DComponentsAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.algorithms.ScaleAlgorithm;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.image.ImageStats;
import org.janelia.colormipsearch.mips.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles 3D volume segmentation for the bidirectional shape match algorithm.
 * Segments the query volume (dilation + connected components), then for each target,
 * intersects with the query to produce a segmented CDM.
 */
class VolumeSegmentationHelper {

    private static class AlignmentSpaceParams {
        final int width;
        final int height;
        final int depth;
        final double voxelSx;
        final double voxelSy;
        final double voxelSz;


        AlignmentSpaceParams(int width, int height, int depth,
                             double voxelSx, double voxelSy, double voxelSz) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.voxelSx = voxelSx;
            this.voxelSy = voxelSy;
            this.voxelSz = voxelSz;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelper.class);
    private static final int[] DILATION_PARAMS = {7, 7, 4};
    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams(1210, 566, 174, 0.5189161, 0.5189161, 1)); // brain
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams(573, 1119, 219, 0.4611220, 0.4611220, 0.7)); // VNC
    }};
    private static final int INITIAL_DOWNSCALE_FACTOR = 2;
    private static final int CONNECTED_COMPS_THRESHOLD = 25;
    private static final int DEFAULT_MIN_CONNECTED_COMP_VOLUME = 300;

    static ComputeVariantImageSupplier get3DVolumeVariant(Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers) {
        // typically only one of these 2 variants is available - either the NRRD segmentation or the SWC
        // so lookup for one
        return Arrays.asList(ComputeFileType.Vol3DSegmentation, ComputeFileType.SkeletonSWC).stream()
                .map(queryVariantsSuppliers::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private final AlignmentSpaceParams asParams;
    private final String query3DVolumeName;
    private final ImageArray query3DVolume;

    VolumeSegmentationHelper(String alignmentSpace,
                             Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers) {
        this.asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters found for " + alignmentSpace);
        }
        // Find the first available query variant (Vol3DSegmentation or SkeletonSWC)
        ComputeVariantImageSupplier queryVolumeSupplier = get3DVolumeVariant(queryVariantsSuppliers);
        if (queryVolumeSupplier != null) {
            this.query3DVolumeName = queryVolumeSupplier.getName();
            LOG.debug("Query volume name {}", query3DVolumeName);
            this.query3DVolume = segmentQueryVolume(queryVolumeSupplier.getImage());
        } else {
            LOG.info("No query 3D-volume provided");
            this.query3DVolumeName = null;
            this.query3DVolume = null;
        }
    }

    boolean isAvailable() {
        return query3DVolume != null;
    }

    /**
     * Generate a segmented color depth MIP by intersecting the target volume with the segmented query volume.
     * Tries both the original and horizontally flipped target, picking the orientation with the larger overlap.
     *
     * @param targetVolume 3D target volume (single-channel)
     * @return 2D RGB color depth MIP, or null if no overlap
     */
    ImageArray generateSegmentedCDM(ImageArray targetVolume) {
        if (query3DVolume == null) {
            LOG.info("No query volume was provided");
            return null;
        }
        if (targetVolume == null) {
            LOG.info("No target volume was provided");
            return null;
        }
        long startCDM = System.currentTimeMillis();
        // AND-mask target with query volume
        ImageArray maskedTarget = ImageOperations.combine2(targetVolume, query3DVolume, (p1, p2) -> p1 & p2);
        int maskedMax = ImageOperations.max(maskedTarget);

        ImageArray largestTargetComponent;
        long unflippedVolume;
        LOG.debug("Masked target max value: {}", maskedMax);
        if (maskedMax > CONNECTED_COMPS_THRESHOLD) {
            Connect3DComponentsAlgorithm.ComponentsResult targetComponentsResult =
                    Connect3DComponentsAlgorithm.findConnectedComponents(maskedTarget, CONNECTED_COMPS_THRESHOLD);
            if (targetComponentsResult.getLargestLabel() > 0 && targetComponentsResult.getLargestComponentSize() > DEFAULT_MIN_CONNECTED_COMP_VOLUME) {
                largestTargetComponent = ImageOperations.duplicateImage(
                        ImageOperations.maskRegion(
                                maskedTarget,
                                new Connect3DComponentsAlgorithm.ComponentLabelRegionPredicate(targetComponentsResult.getLabels(), targetComponentsResult.getLargestLabel())
                        ),
                        Gray16ImageArray::new
                );
                unflippedVolume = targetComponentsResult.getLargestComponentSize();
            } else {
                largestTargetComponent = null;
                unflippedVolume = 0;
            }
            LOG.debug("Largest masked target component size: {}", unflippedVolume);
        } else {
            LOG.debug("Target components not considered for CDM because max value {} is below the threshold", maskedMax);
            largestTargetComponent = null;
            unflippedVolume = 0;
        }
        LOG.trace("Unflipped target volume: {}", unflippedVolume);

        // Try with horizontally flipped target
        ImageArray flippedTarget = ImageOperations.flipImage(targetVolume, Dimensions.X_AXIS);
        ImageArray flippedMaskedTarget = ImageOperations.combine2(flippedTarget,  query3DVolume, (p1, p2) -> p1 & p2);
        int flippedMaskedMax = ImageOperations.max(flippedMaskedTarget);

        ImageArray largestFlippedTargetComponent;
        long flippedVolume;
        LOG.trace("Flipped masked target max value: {}", flippedMaskedMax);
        if (flippedMaskedMax > CONNECTED_COMPS_THRESHOLD) {
            Connect3DComponentsAlgorithm.ComponentsResult flippedTargetComponentsResult =
                    Connect3DComponentsAlgorithm.findConnectedComponents(flippedMaskedTarget, CONNECTED_COMPS_THRESHOLD);
            if (flippedTargetComponentsResult.getLargestLabel() > 0 && flippedTargetComponentsResult.getLargestComponentSize() > DEFAULT_MIN_CONNECTED_COMP_VOLUME) {
                largestFlippedTargetComponent = ImageOperations.duplicateImage(
                        ImageOperations.maskRegion(
                                maskedTarget,
                                new Connect3DComponentsAlgorithm.ComponentLabelRegionPredicate(flippedTargetComponentsResult.getLabels(), flippedTargetComponentsResult.getLargestLabel())
                        ),
                        Gray16ImageArray::new
                );
                flippedVolume = flippedTargetComponentsResult.getLargestComponentSize();
            } else {
                largestFlippedTargetComponent = null;
                flippedVolume = 0;
            }
            LOG.trace("Largest flipped target component size: {}", flippedVolume);
        } else {
            LOG.trace("Flipped target components not considered for CDM because max value {} is below the threshold", flippedMaskedMax);
            largestFlippedTargetComponent = null;
            flippedVolume = 0;
        }
        LOG.trace("Flipped target area: {}", flippedVolume);

        ImageArray cdm;
        if (unflippedVolume == 0 && flippedVolume == 0) {
            LOG.trace("No overlap between query ({}) and the target", query3DVolumeName);
            cdm = null;
        } else if (unflippedVolume >= flippedVolume) {
            LOG.trace("Generate CDM from unflipped");
            cdm = CDMGenerationAlgorithm.generateCDM(largestTargetComponent);
        } else {
            LOG.trace("Generate CDM from flipped");
            cdm = CDMGenerationAlgorithm.generateCDM(largestFlippedTargetComponent);
        }
        long endCDM = System.currentTimeMillis();
        LOG.debug("Complete CDM in {} secs", (endCDM - startCDM) / 1000.);
        return cdm;
    }

    /**
     * Segment the query volume: enhance contrast, dilate, rescale, binarize.
     */
    private ImageArray segmentQueryVolume(ImageArray sourceVolume) {
        if (sourceVolume == null) {
            LOG.info("No query volume could be loaded for {}", query3DVolumeName);
            return null;
        }
        LOG.trace("Downscale {}x{}x{} volume to {}x{}x{}",
                sourceVolume.getWidth(), sourceVolume.getHeight(), sourceVolume.getDepth(),
                asParams.width / INITIAL_DOWNSCALE_FACTOR,
                asParams.height / INITIAL_DOWNSCALE_FACTOR,
                asParams.depth / INITIAL_DOWNSCALE_FACTOR);
        ImageArray downscaledVolume = ScaleAlgorithm.scaleVolume(
                sourceVolume,
                asParams.width / INITIAL_DOWNSCALE_FACTOR,
                asParams.height / INITIAL_DOWNSCALE_FACTOR,
                asParams.depth / INITIAL_DOWNSCALE_FACTOR,
                65535,
                Gray16ImageArray::new);

        // Enhance contrast using z-projection statistics (matches LM_EM_Segmentation behavior)
        ImageArray contrastEnhanced = enhanceContrastUsingMIP(downscaledVolume);

        long startDilation = System.currentTimeMillis();
        ImageArray dilated = MaxFilterAlgorithm.maxGrayFilter3D(
                contrastEnhanced,
                DILATION_PARAMS[0], DILATION_PARAMS[1], DILATION_PARAMS[2]
        );
        long endDilation = System.currentTimeMillis();
        LOG.debug("Completed dilation of {} in {} secs", query3DVolumeName, (endDilation - startDilation) / 1000.);

        // Rescale to alignment space dimensions if different
        LOG.trace("Rescale {}x{}x{} volume to {}x{}x{}",
                dilated.getWidth(), dilated.getHeight(), dilated.getDepth(),
                asParams.width, asParams.height, asParams.depth);
        ImageArray rescaled = ScaleAlgorithm.scaleVolume(dilated, asParams.width, asParams.height, asParams.depth, 65535, Gray16ImageArray::new);

        // Find max value
        int maxValue = ImageOperations.max(rescaled);
        LOG.debug("Rescaled volume of size {}x{}x{} -> max: {} ",
                rescaled.getWidth(), rescaled.getHeight(), rescaled.getDepth(), maxValue);
        int lowerThreshold = maxValue > 2000 ? 2000 : 1;

        // Binarize: set voxels in [lowerThreshold, 65535] range to foreground
        int totalSize = rescaled.getSpatialSize();
        Gray16ImageArray binary = new Gray16ImageArray(rescaled.getWidth(), rescaled.getHeight(), rescaled.getDepth());
        int nforeground = 0;
        for (int pi = 0; pi < totalSize; pi++) {
            int val = rescaled.getPackedIntValAtIndex(pi);
            if (val >= lowerThreshold && val <= 65535) {
                binary.setPackedIntValAtIndex(pi, 65535);
                nforeground++;
            } else {
                binary.setPackedIntValAtIndex(pi, 0);
            }
        }
        LOG.debug("nforeground: {} out of {}", nforeground, totalSize);
        return binary;
    }

    private ImageArray enhanceContrastUsingMIP(ImageArray imageArray) {
        // get min/max from the maximum intensity projection
        ImageArray mip = ImageOperations.maxIntensityProjection(imageArray, 0, imageArray.getDepth(), Gray16ImageArray::new);
        ImageArray contrastEnhancedMIP = ImageOperations.stretchHistogram(mip, 0.35);
        ImageStats mipStats = ImageOperations.getImageStats(contrastEnhancedMIP);
        LOG.trace("MIP stats: {}", mipStats);

        if (mipStats.maxVal > 0 && mipStats.maxVal != 255) {
            double scale = (mipStats.maxVal != mipStats.minVal)
                    ? 255.0 / (mipStats.maxVal - mipStats.minVal)
                    : 255.0 / mipStats.maxVal;
            double offset = -mipStats.minVal * scale;
            return ImageOperations.scaleIntensity(imageArray, 0., 255., scale, offset);
        } else {
            return imageArray;
        }
    }

}
