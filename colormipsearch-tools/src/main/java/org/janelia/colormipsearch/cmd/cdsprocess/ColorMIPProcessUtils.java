package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.cmd.CachedMIPsUtils;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.ScoredEntry;

public class ColorMIPProcessUtils {
    public static <N extends AbstractNeuronEntity, P>
    Map<ComputeFileType, Supplier<ImageAccess<P>>> getGrayVariantImagesSuppliers(Set<ComputeFileType> variantTypes,
                                                                                 N neuronMIP,
                                                                                 P grayPixelType) {
        return NeuronMIPUtils.getImageLoaders(
                neuronMIP,
                variantTypes,
                (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadGrayMIP(n, cft, grayPixelType)));
    }

    public static <N extends AbstractNeuronEntity, P extends RGBPixelType<P>>
    Map<ComputeFileType, Supplier<ImageAccess<P>>> getRGBVariantImagesSuppliers(Set<ComputeFileType> variantTypes,
                                                                                N neuronMIP,
                                                                                P rgbPixelType) {
        return NeuronMIPUtils.getImageLoaders(
                neuronMIP,
                variantTypes,
                (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadRGBMIP(n, cft, rgbPixelType)));
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
