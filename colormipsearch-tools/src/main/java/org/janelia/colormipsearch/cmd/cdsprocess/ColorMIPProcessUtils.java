package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.List;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.ScoredEntry;

public class ColorMIPProcessUtils {
    public static <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> List<CDMatchEntity<M, T>> selectBestMatches(List<CDMatchEntity<M, T>> CDMatches,
                                                                                                                               int topLineMatches,
                                                                                                                               int topSamplesPerLine,
                                                                                                                               int topMatchesPerSample) {
        List<ScoredEntry<List<CDMatchEntity<M, T>>>> topRankedLineMatches = ItemsHandling.selectTopRankedElements(
                CDMatches,
                match -> match.getMatchedImage().getPublishedName(), // group by published name
                CDMatchEntity::getMatchingPixels, // use pixel matching score
                topLineMatches,
                -1);

        return topRankedLineMatches.stream()
                .flatMap(se -> ItemsHandling.selectTopRankedElements( // topRankedSamplesPerLine
                        se.getEntry(),
                        match -> match.getMatchedImage().getNeuronId(),
                        CDMatchEntity::getMatchingPixels,
                        topSamplesPerLine,
                        topMatchesPerSample
                ).stream())
                .flatMap(se -> se.getEntry().stream())
                .collect(Collectors.toList())
                ;
    }
}
