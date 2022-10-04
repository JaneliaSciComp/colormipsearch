package org.janelia.colormipsearch.results;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.janelia.colormipsearch.dto.AbstractMatchedTarget;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.ResultMatches;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileType;

public class MatchResultsGrouping {

    /**
     * The method groups persisted matched neurons by mask and returns the metadata fields only.
     *
     * @param matches persisted matches that need to be grouped by mask.
     * @param maskFieldSelectors mask fields used for grouping
     * @param ranking comparator used for ranking results
     * @param <R> persisted matches type
     * @param <M> metadata type corresponding to the original mask type which will be used for grouping
     * @param <T> metadata type corresponding to the original target type
     * @param <R1> grouped result types
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>,
                   M extends AbstractNeuronMetadata,
                   T extends AbstractNeuronMetadata,
                   R1 extends AbstractMatchedTarget<T>>
    List<ResultMatches<M, R1>> groupByMask(List<R> matches,
                                           List<Function<M, ?>> maskFieldSelectors,
                                           Comparator<R1> ranking) {
        // the group invocation must set match files otherwise the actual match info may be lost
        return ItemsHandling.groupItems(
                matches,
                aMatch -> {
                    R1 matchResult = (R1) aMatch.metadata();
                    AbstractNeuronEntity maskImage = aMatch.getMaskImage();
                    matchResult.setMaskImageInternalId(maskImage.getEntityId());
                    if (aMatch.getMatchedImage() != null) {
                        // target image may be null - specifically for PPP matches
                        AbstractNeuronEntity targetImage = aMatch.getMatchedImage();
                        // the target is set based on the original target
                        matchResult.setTargetImage((T) targetImage.metadata());
                        // only set match files if the target is present as a "backup"
                        matchResult.setMatchFile(FileType.CDMMatch, targetImage.getComputeFileName(ComputeFileType.InputColorDepthImage));
                        matchResult.setMatchFile(FileType.CDMInput, maskImage.getComputeFileName(ComputeFileType.InputColorDepthImage));
                    }
                    return new GroupingCriteria<R1, M>(
                            matchResult,
                            m -> (M) maskImage.metadata(),
                            maskFieldSelectors
                    );
                },
                GroupingCriteria::getItem,
                ranking,
                ResultMatches::new
        );
    }

    /**
     * The method groups persisted matched neurons by target and returns the metadata fields only.
     *
     * @param matches persisted matches that need to be grouped by mask.
     * @param targetFieldSelectors mask fields used for grouping
     * @param ranking comparator used for ranking results
     * @param <R> persisted matches type
     * @param <M> metadata type corresponding to the original mask type which now will be used as target type
     * @param <T> metadata type corresponding to the original target type - used for grouping
     * @param <R1> grouped result types
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>,
                   M extends AbstractNeuronMetadata,
                   T extends AbstractNeuronMetadata,
                   R1 extends AbstractMatchedTarget<M>>
    List<ResultMatches<T, R1>> groupByTarget(List<R> matches,
                                             List<Function<T, ?>> targetFieldSelectors,
                                             Comparator<R1> ranking) {
        // the group invocation must set match files otherwise the actual match info may be lost
        return ItemsHandling.groupItems(
                matches,
                aMatch -> {
                    R1 matchResult = (R1) aMatch.metadata();
                    AbstractNeuronEntity maskImage = aMatch.getMaskImage();
                    AbstractNeuronEntity targetImage = aMatch.getMatchedImage();
                    // in this case actual mask image will be set as target and the target image ID will be set to mask ID
                    matchResult.setMaskImageInternalId(targetImage.getEntityId());
                    matchResult.setTargetImage((M) maskImage.metadata());
                    // set result match files as a backup
                    matchResult.setMatchFile(FileType.CDMInput, targetImage.getComputeFileName(ComputeFileType.InputColorDepthImage));
                    matchResult.setMatchFile(FileType.CDMMatch, maskImage.getComputeFileName(ComputeFileType.InputColorDepthImage));
                    return new GroupingCriteria<R1, T>(
                            matchResult,
                            m -> (T) targetImage.metadata(), // group by target image
                            targetFieldSelectors
                    );
                },
                GroupingCriteria::getItem,
                ranking,
                ResultMatches::new
        );
    }

}
