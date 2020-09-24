package org.janelia.colormipsearch.cmsdrivers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.google.common.collect.Streams;

import org.janelia.colormipsearch.api.cdsearch.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPMatchScore;
import org.janelia.colormipsearch.api.imageprocessing.ImageArray;
import org.janelia.colormipsearch.utils.CachedMIPsUtils;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPSearch;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPSearchResult;
import org.janelia.colormipsearch.api.cdmips.MIPImage;
import org.janelia.colormipsearch.api.cdmips.MIPMetadata;
import org.janelia.colormipsearch.api.cdmips.MIPsUtils;
import org.janelia.colormipsearch.api.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform color depth mask search in the current process.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class LocalColorMIPSearch implements ColorMIPSearchDriver {

    private static final Logger LOG = LoggerFactory.getLogger(LocalColorMIPSearch.class);
    private static final long _1M = 1024 * 1024;

    private final ColorMIPSearch colorMIPSearch;
    private final Executor cdsExecutor;
    private final int libraryPartitionSize;
    private final List<String> gradientsLocations;
    private final MappingFunction<String, String> gradientVariantSuffixMapping;
    private final List<String> zgapMasksLocations;
    private final MappingFunction<String, String> zgapMaskVariantSuffixMapping;

    public LocalColorMIPSearch(ColorMIPSearch colorMIPSearch,
                               int libraryPartitionSize,
                               List<String> gradientsLocations,
                               MappingFunction<String, String> gradientVariantSuffixMapping,
                               List<String> zgapMasksLocations,
                               MappingFunction<String, String> zgapMaskVariantSuffixMapping,
                               Executor cdsExecutor) {
        this.colorMIPSearch = colorMIPSearch;
        this.libraryPartitionSize = libraryPartitionSize > 0 ? libraryPartitionSize : 1;
        this.gradientsLocations = gradientsLocations;
        this.gradientVariantSuffixMapping = gradientVariantSuffixMapping;
        this.zgapMasksLocations = zgapMasksLocations;
        this.zgapMaskVariantSuffixMapping = zgapMaskVariantSuffixMapping;
        this.cdsExecutor = cdsExecutor;
    }

    @Override
    public List<ColorMIPSearchResult> findAllColorDepthMatches(List<MIPMetadata> maskMIPS,
                                                               List<MIPMetadata> libraryMIPS) {
        long startTime = System.currentTimeMillis();
        int nmasks = maskMIPS.size();
        int nlibraries = libraryMIPS.size();

        LOG.info("Searching {} masks against {} libraries", nmasks, nlibraries);

        List<CompletableFuture<List<ColorMIPSearchResult>>> allColorDepthSearches = Streams.zip(
                LongStream.range(0, maskMIPS.size()).boxed(),
                maskMIPS.stream().filter(MIPsUtils::exists),
                (mIndex, maskMIP) -> submitMaskSearches(mIndex + 1, maskMIP, libraryMIPS))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        LOG.info("Submitted all {} color depth searches for {} masks with {} libraries in {}s - memory usage {}M",
                allColorDepthSearches.size(), maskMIPS.size(), libraryMIPS.size(),
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);

        List<ColorMIPSearchResult> allSearchResults = CompletableFuture.allOf(allColorDepthSearches.toArray(new CompletableFuture<?>[0]))
                .thenApply(ignoredVoidResult -> allColorDepthSearches.stream()
                        .flatMap(searchComputation -> searchComputation.join().stream())
                        .collect(Collectors.toList()))
                .join();

        LOG.info("Finished all color depth searches {} masks with {} libraries in {}s - memory usage {}M",
                maskMIPS.size(), libraryMIPS.size(), (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);
        return allSearchResults;
    }

    private List<CompletableFuture<List<ColorMIPSearchResult>>> submitMaskSearches(long mIndex,
                                                                                   MIPMetadata maskMIP,
                                                                                   List<MIPMetadata> libraryMIPs) {
        MIPImage maskImage = MIPsUtils.loadMIP(maskMIP); // load image - no caching for the mask
        if (maskImage == null) {
            return Collections.singletonList(
                    CompletableFuture.completedFuture(Collections.emptyList())
            );
        }
        ColorDepthSearchAlgorithm<ColorMIPMatchScore> maskColorDepthSearch = colorMIPSearch.createMaskColorDepthSearch(maskImage, null);
        Set<String> requiredVariantTypes = maskColorDepthSearch.getRequiredTargetVariantTypes();
        List<CompletableFuture<List<ColorMIPSearchResult>>> cdsComputations = Utils.partitionList(libraryMIPs, libraryPartitionSize).stream()
                .map(libraryMIPsPartition -> {
                    Supplier<List<ColorMIPSearchResult>> searchResultSupplier = () -> {
                        LOG.debug("Compare mask# {} - {} with {} out of {} libraries", mIndex, maskMIP, libraryMIPsPartition.size(), libraryMIPs.size());
                        long startTime = System.currentTimeMillis();
                        List<ColorMIPSearchResult> srs = libraryMIPsPartition.stream()
                                .filter(MIPsUtils::exists)
                                .map(libraryMIP -> {
                                    try {
                                        MIPImage libraryImage = CachedMIPsUtils.loadMIP(libraryMIP);
                                        Map<String, Supplier<ImageArray>> variantImageSuppliers = new HashMap<>();
                                        if (requiredVariantTypes.contains("gradient")) {
                                            variantImageSuppliers.put("gradient", () -> {
                                                MIPImage gradientImage = CachedMIPsUtils.loadMIP(MIPsUtils.getMIPVariantInfo(
                                                        libraryMIP,
                                                        "gradient",
                                                        gradientsLocations,
                                                        gradientVariantSuffixMapping));
                                                return MIPsUtils.getImageArray(gradientImage);
                                            });
                                        }
                                        if (requiredVariantTypes.contains("zgap")) {
                                            variantImageSuppliers.put("zgap", () -> {
                                                MIPImage libraryZGapMaskImage = CachedMIPsUtils.loadMIP(MIPsUtils.getMIPVariantInfo(
                                                        libraryMIP,
                                                        "zgap",
                                                        zgapMasksLocations,
                                                        zgapMaskVariantSuffixMapping));
                                                return MIPsUtils.getImageArray(libraryZGapMaskImage);
                                            });
                                        }
                                        ColorMIPMatchScore colorMIPMatchScore = maskColorDepthSearch.calculateMatchingScore(
                                                MIPsUtils.getImageArray(libraryImage),
                                                variantImageSuppliers);
                                        boolean isMatch = colorMIPSearch.isMatch(colorMIPMatchScore);
                                        return new ColorMIPSearchResult(
                                                maskMIP,
                                                libraryMIP,
                                                colorMIPMatchScore,
                                                isMatch,
                                                false);
                                    } catch (Throwable e) {
                                        LOG.warn("Error comparing mask {} with {}", maskMIP,  libraryMIP, e);
                                        return new ColorMIPSearchResult(
                                                maskMIP,
                                                libraryMIP,
                                                ColorMIPMatchScore.NO_MATCH,
                                                false,
                                                true);
                                    }
                                })
                                .filter(ColorMIPSearchResult::isMatch)
                                .collect(Collectors.toList());
                        LOG.info("Found {} results with matches comparing mask# {} - {} with {} out of {} libraries in {}ms",
                                srs.size(), mIndex, maskMIP, libraryMIPsPartition.size(), libraryMIPs.size(), System.currentTimeMillis() - startTime);
                        return srs;
                    };
                    return CompletableFuture.supplyAsync(searchResultSupplier, cdsExecutor);
                })
                .collect(Collectors.toList());
        LOG.info("Submitted {} partitioned color depth searches with {} libraries for mask# {} - {}",
                cdsComputations.size(), libraryMIPs.size(), mIndex, maskMIP);
        return cdsComputations;
    }

    @Override
    public void terminate() {
        // nothing to do here
    }
}
