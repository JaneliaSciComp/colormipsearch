package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.cds.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.cmd.CachedMIPsUtils;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.ScoredEntry;

public class ColorMIPProcessUtils {

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> getTargetVariantImageSuppliers(Set<ComputeFileType> variantTypes,
                                                                                                                                         N neuronMIP) {
        return NeuronMIPUtils.getImageProviders(neuronMIP, variantTypes, CachedMIPsUtils::loadMIP);
    }

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> getQueryVariantImageSuppliers(Set<ComputeFileType> variantTypes,
                                                                                                              N neuronMIP) {
        return NeuronMIPUtils.getImageProviders(neuronMIP, variantTypes, NeuronMIPUtils::loadQueryVariant);
    }

    public static <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> List<CDMatchEntity<M, T>> selectBestMatches(List<CDMatchEntity<M, T>> CDMatches,
                                                                                                                               int topLineMatches,
                                                                                                                               int topSamplesPerLine,
                                                                                                                               int topMatchesPerSample) {
        List<ScoredEntry<List<CDMatchEntity<M, T>>>> topRankedLineMatches = ItemsHandling.selectTopRankedElements(
                CDMatches,
                match -> match.getMatchedImage().getPublishedName(),
                CDMatchEntity::getMatchingPixels,
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
