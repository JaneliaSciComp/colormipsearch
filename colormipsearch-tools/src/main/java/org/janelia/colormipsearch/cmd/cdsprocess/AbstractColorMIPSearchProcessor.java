package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cds.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.cds.PixelMatchScore;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.ProcessingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractColorMIPSearchProcessor<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
        implements ColorMIPSearchProcessor<M, T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractColorMIPSearchProcessor.class);

    final Number cdsRunId;
    final ColorMIPSearch colorMIPSearch;
    final int localProcessingPartitionSize;
    final Set<String> tags;

    AbstractColorMIPSearchProcessor(Number cdsRunId,
                                    ColorMIPSearch colorMIPSearch,
                                    int localProcessingPartitionSize,
                                    Set<String> tags) {
        this.cdsRunId = cdsRunId;
        this.colorMIPSearch = colorMIPSearch;
        this.localProcessingPartitionSize = localProcessingPartitionSize > 0 ? localProcessingPartitionSize : 1;
        this.tags = tags;
    }

    /**
     * Applies the given algorithm to find the pixel matches.
     *
     * @param cdsAlgorithm
     * @param maskImage
     * @param targetImage
     * @return null if no match was found otherwise it returns a @CDSMatch@
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    CDMatchEntity<M, T> findPixelMatch(
            ColorDepthSearchAlgorithm<PixelMatchScore> cdsAlgorithm,
            NeuronMIP<M> maskImage,
            NeuronMIP<T> targetImage) {
        CDMatchEntity<M, T> result = new CDMatchEntity<>();
        // set the mask and the target with the corresponding processing tags set
        // I am wondering if this has a big cost considering that the processed tags for the mask can be set only once
        result.setMaskImage((M) maskImage.getNeuronInfo().addProcessedTags(ProcessingType.ColorDepthSearch, tags));
        result.setMatchedImage((T) targetImage.getNeuronInfo().addProcessedTags(ProcessingType.ColorDepthSearch, tags));
        try {
            Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> targetVariantImageSuppliers =
                    ColorMIPProcessUtils.getTargetVariantImageSuppliers(
                            cdsAlgorithm.getRequiredTargetVariantTypes(),
                            targetImage.getNeuronInfo());

            PixelMatchScore pixelMatchScore = cdsAlgorithm.calculateMatchingScore(
                    (RandomAccessibleInterval<? extends RGBPixelType<?>>) NeuronMIPUtils.getImageArray(targetImage),
                    targetVariantImageSuppliers);
            LOG.debug("Compare pixel match score between {} and {} -> {}({})",
                    maskImage, targetImage, pixelMatchScore.getScore(), pixelMatchScore.isMirrored());
            result.setSessionRefId(cdsRunId);
            result.setMatchFound(colorMIPSearch.isMatch(pixelMatchScore));
            result.setMatchingPixels(pixelMatchScore.getScore());
            result.setMatchingPixelsRatio(pixelMatchScore.getNormalizedScore());
            result.setMirrored(pixelMatchScore.isMirrored());
            result.setNormalizedScore(pixelMatchScore.getNormalizedScore());
            result.addAllTags(tags);
        } catch (Throwable e) {
            LOG.warn("Error comparing mask {} with {}", maskImage, targetImage, e);
            result.setErrors(e.getMessage());
        }
        return result;
    }
}
