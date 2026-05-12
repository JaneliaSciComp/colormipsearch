package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.mips.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.ScoredEntry;

public class ColorMIPProcessUtils {

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier> getTargetVariantImageSuppliers(Set<ComputeFileType> variantTypes, N neuronMIP) {
        // target variants are cached
        return NeuronMIPUtils.getImageSuppliers(neuronMIP, variantTypes, CachedMIPsUtils::loadMIP);
    }

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier> getQueryVariantImageSuppliers(Set<ComputeFileType> variantTypes, N neuronMIP) {
        // query variants are not cached
        return NeuronMIPUtils.getImageSuppliers(neuronMIP, variantTypes, NeuronMIPUtils::loadComputeFile);
    }

    public static <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> List<CDMatchEntity<M, T>> selectBestMatches(List<CDMatchEntity<M, T>> cdMatchEntities,
                                                                                                                               int topLineMatches,
                                                                                                                               int topSamplesPerLine,
                                                                                                                               int topMatchesPerSample) {
        List<ScoredEntry<List<CDMatchEntity<M, T>>>> topRankedLineMatches = ItemsHandling.selectTopRankedElements(
                cdMatchEntities,
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
