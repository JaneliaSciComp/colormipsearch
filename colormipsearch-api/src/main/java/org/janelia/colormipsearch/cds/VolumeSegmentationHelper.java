package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.janelia.colormipsearch.image.Gray8ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.image.algorithms.CDMGenerationAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Connect3DComponentsAlgorithm;
import org.janelia.colormipsearch.image.algorithms.ContrastEnhancer;
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
    private static final int CONNECTED_COMPS_THRESHOLD = 25;
    private static final int CONNECTED_COMPS_MIN_VOLUME = 300;

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
        if (query3DVolume == null || targetVolume == null) {
            LOG.info("Mask or target volume is null");
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
            unflippedVolume = ImageOperations.countNotBg(largestMaskedComponent);
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
            flippedVolume = ImageOperations.countNotBg(largestFlippedComponent);
        } else {
            largestFlippedComponent = flippedMaskedTarget;
            flippedVolume = 0;
        }
        LOG.trace("Flipped target area: {}", flippedVolume);

        ImageArray cdm;
        if (unflippedVolume >= flippedVolume) {
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
     * Segment the query volume: enhance contrast, dilate, rescale, binarize.
     */
    private ImageArray segmentQueryVolume(ImageArray sourceVolume) {
        if (sourceVolume == null) {
            LOG.info("No query volume could be loaded for {}", query3DVolumeName);
            return null;
        }
        ImageArray scaledSourceVolume = ScaleAlgorithm.scaleVolume(
                sourceVolume,
                asParams.width / 2, asParams.height / 2, asParams.depth / 2,
                Gray16ImageArray::new);

        // Enhance contrast using z-projection statistics (matches LM_EM_Segmentation behavior)
        ImageArray contrastEnhanced = ContrastEnhancer.enhanceContrastUsingZProjection(scaledSourceVolume);

        long startDilation = System.currentTimeMillis();
        ImageArray dilated = MaxFilterAlgorithm.maxGrayFilter3D(
                contrastEnhanced,
                DILATION_PARAMS[0], DILATION_PARAMS[1], DILATION_PARAMS[2]
        );
        long endDilation = System.currentTimeMillis();
        LOG.debug("Completed dilation of {} in {} secs", query3DVolumeName, (endDilation - startDilation) / 1000.);
        // Rescale to alignment space dimensions if different
        ImageArray rescaled = ScaleAlgorithm.scaleVolume(dilated, asParams.width, asParams.height, asParams.depth, Gray16ImageArray::new);

        // Find max value
        int maxValue = ImageOperations.max(rescaled);
        int lowerThreshold = maxValue > 2000 ? 2000 : 1;

        // Binarize: set voxels in [lowerThreshold, 65535] range to foreground
        int totalSize = rescaled.getSpatialSize();
        Gray16ImageArray binary = new Gray16ImageArray(rescaled.getWidth(), rescaled.getHeight(), rescaled.getDepth());
        for (int pi = 0; pi < totalSize; pi++) {
            int val = rescaled.getPackedIntValAtIndex(pi);
            if (val >= lowerThreshold && val <= 65535) {
                binary.setPackedIntValAtIndex(pi, 65535);
            }
        }
        return binary;
    }

}
