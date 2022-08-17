package org.janelia.colormipsearch.results;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.janelia.colormipsearch.dto.AbstractMatchedTarget;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.ResultMatches;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.FileType;

public class MatchResultsGrouping {

    /**
     * The method groups persisted matched neurons by mask and returns the metadata fields only.
     *
     * @param matches persisted matches that need to be grouped by mask.
     * @param maskFieldSelectors mask fields used for grouping
     * @param ranking comparator used for ranking results
     * @param <R> persisted matches type
     * @param <M> grouping type
     * @param <R1> grouped result types
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>,
                   M extends AbstractNeuronMetadata,
                   R1 extends AbstractMatchedTarget<? extends AbstractNeuronMetadata>>
    List<ResultMatches<M, R1>> groupByMask(List<R> matches,
                                           List<Function<M, ?>> maskFieldSelectors,
                                           Comparator<R1> ranking) {
        return ItemsHandling.groupItems(
                matches,
                aMatch -> {
                    R1 matchResult = (R1) aMatch.metadata();
                    AbstractNeuronEntity maskImage = aMatch.getMaskImage();
                    AbstractNeuronEntity targetImage = aMatch.getMatchedImage();
                    // in the match result input file comes from the mask and matched file comes from the target
                    matchResult.setMatchFile(FileType.ColorDepthMipInput, maskImage.getNeuronFile(FileType.ColorDepthMipInput));
                    matchResult.setMatchFile(FileType.ColorDepthMipMatch, targetImage.getNeuronFile(FileType.ColorDepthMipInput));
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
     * @param <T> metadata type corresponding to the original mask type which now will be used as target type
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
        return ItemsHandling.groupItems(
                matches,
                aMatch -> {
                    R1 matchResult = (R1) aMatch.metadata();
                    AbstractNeuronEntity maskImage = aMatch.getMaskImage();
                    AbstractNeuronEntity targetImage = aMatch.getMatchedImage();
                    // in this case actual mask image will be set as target
                    matchResult.setTargetImage((M) maskImage.metadata());
                    // !!!! FIXME
                    // in the match result input file comes from the target and matched file comes from the mask
                    matchResult.setMatchFile(FileType.ColorDepthMipInput, targetImage.getNeuronFile(FileType.ColorDepthMipInput));
                    matchResult.setMatchFile(FileType.ColorDepthMipMatch, maskImage.getNeuronFile(FileType.ColorDepthMipInput));
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
