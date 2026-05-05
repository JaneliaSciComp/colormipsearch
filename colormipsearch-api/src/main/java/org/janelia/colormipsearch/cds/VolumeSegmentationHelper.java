package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ShortImageArray;
import org.janelia.colormipsearch.image.algorithms.CDMGenerationAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Connect3DComponentsAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.algorithms.ScaleAlgorithm;
import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
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

        AlignmentSpaceParams(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelper.class);
    private static final int[] DILATION_PARAMS = {7, 7, 4};
    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams(1210, 566, 174)); // brain
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams(573, 1119, 219)); // VNC
    }};
    private static final int CONNECTED_COMPS_THRESHOLD = 25;
    private static final int CONNECTED_COMPS_MIN_VOLUME = 300;

    private final AlignmentSpaceParams asParams;
    private final String query3DVolumeName;
    private final ImageArray query3DVolume;

    VolumeSegmentationHelper(String alignmentSpace,
                             Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers) {
        this.asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters found for " + alignmentSpace);
        }
        // Find the first available query variant (Vol3DSegmentation or SkeletonSWC)
        Supplier<ImageArray> queryVolumeSupplier = getFirstAvailableVariant(queryVariantsSuppliers);
        if (queryVolumeSupplier != null) {
            this.query3DVolumeName = get3DVolumeVariantName(queryVariantsSuppliers);
            this.query3DVolume = segmentQueryVolume(queryVolumeSupplier.get());
        } else {
            LOG.info("No query 3D-volume provided");
            this.query3DVolumeName = null;
            this.query3DVolume = null;
        }
    }

    private static Supplier<ImageArray> getFirstAvailableVariant(Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers) {
        return Arrays.asList(ComputeFileType.Vol3DSegmentation, ComputeFileType.SkeletonSWC).stream()
                .map(queryVariantsSuppliers::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String get3DVolumeVariantName(Map<ComputeFileType, Supplier<ImageArray>> queryVariantsSuppliers) {
        // typically only one of these 2 variants is available - either the NRRD segmentation or the SWC
        // so lookup for one
        return Arrays.asList(ComputeFileType.Vol3DSegmentation, ComputeFileType.SkeletonSWC).stream()
                .filter(queryVariantsSuppliers::containsKey)
                .map(ComputeFileType::name)
                .findFirst()
                .orElse(null);
    }

    String getQuery3DVolumeName() {
        return query3DVolumeName;
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
        if (query3DVolume == null || targetVolume == null) {
            LOG.trace("Mask or target volume is null");
            return null;
        }
        long startCDM = System.currentTimeMillis();
        // AND-mask target with query volume
        ImageArray maskedTarget = ImageOperations.combine2(
                targetVolume,
                query3DVolume,
                (v1, v2) -> {
                    if (v1 > 0 && v2 > 0) {
                        return Math.min(v1, v2);
                    } else {
                        return 0;
                    }
                });
        int maskedMax = ImageOperations.max(maskedTarget);

        ImageArray largestMaskedComponent;
        long unflippedVolume;
        LOG.trace("Masked target max value: {}", maskedMax);
        if (maskedMax > CONNECTED_COMPS_THRESHOLD) {
            largestMaskedComponent = Connect3DComponentsAlgorithm.findLargestComponent(
                    maskedTarget, CONNECTED_COMPS_THRESHOLD, CONNECTED_COMPS_MIN_VOLUME
            );
            unflippedVolume = ImageOperations.nonZeroCount(largestMaskedComponent);
        } else {
            largestMaskedComponent = maskedTarget;
            unflippedVolume = 0;
        }
        LOG.trace("Unflipped target area: {}", unflippedVolume);

        // Try with horizontally flipped target
        ImageArray flippedTarget = ImageOperations.flipImage(targetVolume, Dimensions.X_AXIS);
        ImageArray flippedMaskedTarget = ImageOperations.combine2(
                flippedTarget,
                query3DVolume,
                (v1, v2) -> {
                    if (v1 > 0 && v2 > 0) {
                        return Math.min(v1, v2);
                    } else {
                        return 0;
                    }
                });
        int flippedMaskedMax = ImageOperations.max(flippedMaskedTarget);

        ImageArray largestFlippedComponent;
        long flippedVolume;
        LOG.trace("Flipped masked target max value: {}", flippedMaskedMax);
        if (flippedMaskedMax > CONNECTED_COMPS_THRESHOLD) {
            largestFlippedComponent = Connect3DComponentsAlgorithm.findLargestComponent(
                    flippedMaskedTarget, CONNECTED_COMPS_THRESHOLD, CONNECTED_COMPS_MIN_VOLUME
            );
            flippedVolume = ImageOperations.nonZeroCount(largestFlippedComponent);
        } else {
            largestFlippedComponent = flippedMaskedTarget;
            flippedVolume = 0;
        }
        LOG.trace("Flipped target area: {}", flippedVolume);

        ImageArray cdm;
        if (unflippedVolume == 0 && flippedVolume == 0) {
            LOG.info("No overlap between query ({}) and the target", query3DVolumeName);
            cdm = null;
        } else if (unflippedVolume >= flippedVolume) {
            LOG.trace("Generate CDM from unflipped");
            cdm = CDMGenerationAlgorithm.generateCDM(largestMaskedComponent);
        } else {
            LOG.trace("Generate CDM from flipped");
            cdm = CDMGenerationAlgorithm.generateCDM(largestFlippedComponent);
        }
        long endCDM = System.currentTimeMillis();
        LOG.info("Complete CDM in {} secs", (endCDM - startCDM) / 1000.);
        return cdm;
    }

    /**
     * Segment the query volume: dilate, then find largest connected component.
     */
    private ImageArray segmentQueryVolume(ImageArray sourceVolume) {
        if (sourceVolume == null) {
            LOG.info("No query volume could be loaded for {}", query3DVolumeName);
            return null;
        }
        long startDilation = System.currentTimeMillis();
        ImageArray dilated = MaxFilterAlgorithm.maxGrayFilter3D(
                sourceVolume,
                DILATION_PARAMS[0], DILATION_PARAMS[1], DILATION_PARAMS[2]
        );
        long endDilation = System.currentTimeMillis();
        LOG.debug("Completed dilation of {} in {} secs", query3DVolumeName, (endDilation - startDilation) / 1000.);

        // Rescale to alignment space dimensions if different
        ImageArray rescaled = ScaleAlgorithm.scaleVolume(dilated, asParams.width, asParams.height, asParams.depth);

        // Find max value
        int maxValue = ImageOperations.max(rescaled);
        int lowerThreshold = maxValue > 2000 ? 2000 : 1;

        // Binarize: set voxels in [lowerThreshold, 65535] range to foreground
        int totalSize = rescaled.getSpatialSize();
        ShortImageArray binary = new ShortImageArray(rescaled.getWidth(), rescaled.getHeight(), rescaled.getDepth(), 1);
        for (int pi = 0; pi < totalSize; pi++) {
            int val = rescaled.getPackedIntValAtIndex(pi);
            if (val >= lowerThreshold && val <= 65535) {
                binary.setPackedIntValAtIndex(pi, 65535);
            }
        }
        return binary;
    }

}
