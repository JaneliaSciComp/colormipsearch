package org.janelia.colormipsearch.cmd_v2;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api_v2.cdmips.MIPImage;
import org.janelia.colormipsearch.api_v2.cdmips.MIPMetadata;
import org.janelia.colormipsearch.api_v2.cdmips.MIPsUtils;
import org.janelia.colormipsearch.api_v2.cdsearch.CDSMatches;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorDepthSearchAlgorithmProviderFactory;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorMIPSearchMatchMetadata;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorMIPSearchResultUtils;
import org.janelia.colormipsearch.api_v2.cdsearch.GradientAreaGapUtils;
import org.janelia.colormipsearch.api_v2.cdsearch.NegativeColorDepthMatchScore;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
class CalculateNegativeScoresCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(CalculateNegativeScoresCmd.class);

    @Parameters(commandDescription = "Calculate gradient area score for the results")
    static class NegativeScoreResultsArgs extends AbstractColorDepthMatchArgs {
        @Parameter(names = {"--resultsDir", "-rd"}, converter = ListArg.ListArgConverter.class, description = "Results directory to be sorted")
        ListArg resultsDir;

        @Parameter(names = {"--resultsFile", "-rf"}, variableArity = true, description = "File containing results to be sorted")
        List<String> resultsFiles;

        @Parameter(names = {"--topPublishedNameMatches"},
                description = "If set only calculate the gradient score for the specified number of best lines color depth search results")
        int numberOfBestLines;

        @Parameter(names = {"--topPublishedSampleMatches"},
                description = "If set select the specified numnber of best samples for each line to calculate the gradient score")
        int numberOfBestSamplesPerLine;

        @Parameter(names = {"--topMatchesPerSample"},
                description = "Number of best matches for each line to be used for gradient scoring (defaults to 1)")
        int numberOfBestMatchesPerSample;

        NegativeScoreResultsArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        Path getOutputDir() {
            if (resultsDir == null && StringUtils.isBlank(commonArgs.outputDir)) {
                return null;
            } else if (StringUtils.isNotBlank(commonArgs.outputDir)) {
                return Paths.get(commonArgs.outputDir);
            } else {
                return Paths.get(resultsDir.input);
            }
        }

        @Override
        List<String> validate() {
            List<String> errors = new ArrayList<>();
            boolean inputFound = resultsDir != null || CollectionUtils.isNotEmpty(resultsFiles);
            if (!inputFound) {
                errors.add("No result file or directory containing results has been specified");
            }
            return errors;
        }
    }

    private final NegativeScoreResultsArgs args;
    private final Supplier<Long> cacheSizeSupplier;

    CalculateNegativeScoresCmd(String commandName,
                               CommonArgs commonArgs,
                               Supplier<Long> cacheSizeSupplier) {
        super(commandName);
        this.args = new NegativeScoreResultsArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
    }

    @Override
    NegativeScoreResultsArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        CmdUtils.createOutputDirs(args.getOutputDir());
        // initialize the cache
        CachedMIPsUtils.initializeCache(cacheSizeSupplier.get());
        calculateGradientAreaScore(args);
    }

    private void calculateGradientAreaScore(NegativeScoreResultsArgs args) {
        ImageRegionDefinition labelRegionsProvider = CmdUtils.getLabelsRegionGenerator(args);
        ColorDepthSearchAlgorithmProvider<NegativeColorDepthMatchScore> negativeMatchCDSArgorithmProvider =
                ColorDepthSearchAlgorithmProviderFactory.createNegativeMatchCDSAlgorithmProvider(
                        args.mirrorMask,
                        args.negativeRadius,
                        args.borderSize,
                        loadQueryROIMask(args.queryROIMaskName),
                        labelRegionsProvider);
        Executor executor = CmdUtils.createCDSExecutor(args.commonArgs);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<String> filesToProcess;
        if (CollectionUtils.isNotEmpty(args.resultsFiles)) {
            filesToProcess = args.resultsFiles;
        } else if (args.resultsDir != null) {
            filesToProcess = CmdUtils.getFileToProcessFromDir(args.resultsDir.input, args.resultsDir.offset, args.resultsDir.length);
        } else {
            filesToProcess = Collections.emptyList();
        }
        Path outputDir = args.getOutputDir();
        long startTime = System.currentTimeMillis();
        int nFiles = filesToProcess.size();
        ItemsHandling.partitionCollection(filesToProcess, args.processingPartitionSize).entrySet().stream().parallel()
                .forEach(indexedPartition -> {
                    long startProcessingPartitionTime = System.currentTimeMillis();
                    indexedPartition.getValue().forEach(fn -> {
                        File f = new File(fn);
                        CDSMatches cdsMatches = calculateGradientAreaScoreForResultsFile(
                                negativeMatchCDSArgorithmProvider,
                                f,
                                args.librarySuffix,
                                args.gradientVariantKey,
                                args.gradientPaths,
                                args.gradientSuffix,
                                args.zgapVariantKey,
                                args.zgapPaths,
                                StringUtils.defaultString(args.zgapSuffix, ""),
                                args.numberOfBestLines,
                                args.numberOfBestSamplesPerLine,
                                args.numberOfBestMatchesPerSample,
                                mapper,
                                executor
                        );
                        ColorMIPSearchResultUtils.writeCDSMatchesToJSONFile(
                                cdsMatches,
                                CmdUtils.getOutputFile(outputDir, f),
                                args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter());
                    });
                    LOG.info("Finished a batch of {} in {}s - memory usage {}M out of {}M",
                            indexedPartition.getValue().size(),
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                });
        LOG.info("Finished calculating gradient scores for {} files in {}s - memory usage {}M out of {}M",
                nFiles,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private ImageArray<?> loadQueryROIMask(String queryROIMask) {
        if (StringUtils.isBlank(queryROIMask)) {
            return null;
        } else {
            List<MIPMetadata> queryROIMIPs = MIPsUtils.readMIPsFromLocalFiles(queryROIMask, 0, 1, Collections.emptySet());
            return queryROIMIPs.stream()
                    .findFirst()
                    .map(MIPsUtils::loadMIP)
                    .map(MIPImage::getImageArray)
                    .orElse(null);
        }
    }

    private CDSMatches calculateGradientAreaScoreForResultsFile(
            ColorDepthSearchAlgorithmProvider<NegativeColorDepthMatchScore> negativeMatchCDSArgorithmProvider,
            File inputResultsFile,
            String targetSuffix,
            String gradientVariantKey,
            List<String> gradientsLocations,
            String gradientSuffix,
            String zgapVariantKey,
            List<String> zgapsLocations,
            String zgapsSuffix,
            int numberOfBestLinesToSelect,
            int numberOfBestSamplesPerLineToSelect,
            int numberOfBestMatchesPerSampleToSelect,
            ObjectMapper mapper,
            Executor executor) {
        CDSMatches matchesFileContent = ColorMIPSearchResultUtils.readCDSMatchesFromJSONFile(inputResultsFile, mapper);
        if (CollectionUtils.isEmpty(matchesFileContent.results)) {
            LOG.error("No color depth search results found in {}", inputResultsFile);
            return matchesFileContent;
        }
        LOG.info("Select best matches: {}", numberOfBestLinesToSelect);
        Map<MIPMetadata, List<ColorMIPSearchMatchMetadata>> resultsGroupedByQuery = ColorMIPSearchResultUtils.selectCDSResultForGradientScoreCalculation(
                matchesFileContent.results,
                numberOfBestLinesToSelect,
                numberOfBestSamplesPerLineToSelect,
                numberOfBestMatchesPerSampleToSelect);
        LOG.info("Read {} entries ({} distinct mask MIPs) from {}", matchesFileContent.results.size(), resultsGroupedByQuery.size(), inputResultsFile);

        String cdsResultsSource = inputResultsFile.getAbsolutePath();
        long startTime = System.currentTimeMillis();
        List<CompletableFuture<List<ColorMIPSearchMatchMetadata>>> negativeScoresComputations =
                resultsGroupedByQuery.entrySet().stream().parallel()
                        .map(queryResultsEntry -> {
                            long negativeComputationsStartTime = System.currentTimeMillis();
                            List<CompletableFuture<Long>> negativeScoreComputations = createNegativeScoreComputations(
                                    cdsResultsSource,
                                    queryResultsEntry.getKey(),
                                    queryResultsEntry.getValue(),
                                    targetSuffix,
                                    gradientVariantKey,
                                    gradientsLocations,
                                    gradientSuffix,
                                    zgapVariantKey,
                                    zgapsLocations,
                                    zgapsSuffix,
                                    negativeMatchCDSArgorithmProvider,
                                    executor);
                            return collectNegativeScores(
                                    cdsResultsSource,
                                    queryResultsEntry.getKey(),
                                    queryResultsEntry.getValue(),
                                    negativeScoreComputations,
                                    negativeComputationsStartTime,
                                    executor);
                        })
                        .collect(Collectors.toList());
        List<ColorMIPSearchMatchMetadata> srWithNegativeScores = negativeScoresComputations.stream().parallel()
                .flatMap(gsc -> gsc.join().stream())
                .collect(Collectors.toList());
        LOG.info("Finished gradient area score for {} out of {} entries from {} in {}s - memory usage {}M",
                srWithNegativeScores.size(),
                matchesFileContent.results.size(),
                inputResultsFile,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);
        ColorMIPSearchResultUtils.sortCDSResults(srWithNegativeScores);
        LOG.info("Finished sorting by gradient area score for {} out of {} entries from {} in {}s - memory usage {}M",
                srWithNegativeScores.size(),
                matchesFileContent.results.size(),
                inputResultsFile,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);
        return CDSMatches.singletonfromResultsOfColorMIPSearchMatches(srWithNegativeScores);
    }

    private List<CompletableFuture<Long>> createNegativeScoreComputations(String cdsMatchesSource,
                                                                          MIPMetadata inputQueryMIP,
                                                                          List<ColorMIPSearchMatchMetadata> selectedCDSResultsForQueryMIP,
                                                                          String targetSuffix,
                                                                          String gradientVariantKey,
                                                                          List<String> gradientsLocations,
                                                                          String gradientSuffix,
                                                                          String zgapVariantKey,
                                                                          List<String> zgapsLocations,
                                                                          String zgapsSuffix,
                                                                          ColorDepthSearchAlgorithmProvider<NegativeColorDepthMatchScore> negativeMatchCDSArgorithmProvider,
                                                                          Executor executor) {
        LOG.info("Prepare gradient score computations for {} with {} entries from {}", inputQueryMIP, selectedCDSResultsForQueryMIP.size(), cdsMatchesSource);
        LOG.info("Load query image {}", inputQueryMIP);
        MIPImage inputQueryImage = MIPsUtils.loadMIP(inputQueryMIP); // no caching for the mask
        if (inputQueryImage == null) {
            LOG.error("No image found for {}", inputQueryMIP);
            return Collections.emptyList();
        }
        ColorDepthSearchAlgorithm<NegativeColorDepthMatchScore> gradientScoreCalculator =
                negativeMatchCDSArgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(inputQueryImage.getImageArray(), args.maskThreshold, args.borderSize);
        return selectedCDSResultsForQueryMIP.stream()
                .map(csr -> CompletableFuture.supplyAsync(() -> {
                    long startGapCalcTime = System.currentTimeMillis();
                    Set<String> requiredVariantTypes = gradientScoreCalculator.getRequiredTargetVariantTypes();
                    MIPMetadata matchedMIP = ColorMIPSearchMatchMetadata.getTargetMIP(csr);
                    MIPImage matchedImage = CachedMIPsUtils.loadMIP(matchedMIP);
                    LOG.debug("Processing {} - loaded MIP {} for calculating area gap in {}ms",
                            cdsMatchesSource, inputQueryMIP, System.currentTimeMillis() - startGapCalcTime);
                    long negativeScore;
                    if (matchedImage != null) {
                        // only calculate the area gap if the gradient exist
                        LOG.debug("Processing {} - calculate negative score between {} and {}",
                                cdsMatchesSource, inputQueryMIP, matchedMIP);
                        Map<String, Supplier<ImageArray<?>>> variantImageSuppliers = new HashMap<>();
                        if (requiredVariantTypes.contains("gradient")) {
                            variantImageSuppliers.put("gradient", createVariantImageSupplier(matchedMIP, gradientVariantKey, gradientsLocations, gradientSuffix, targetSuffix));
                        }
                        if (requiredVariantTypes.contains("zgap")) {
                            variantImageSuppliers.put("zgap", createVariantImageSupplier(matchedMIP, zgapVariantKey, zgapsLocations, zgapsSuffix, targetSuffix));
                        }
                        NegativeColorDepthMatchScore negativeScores = gradientScoreCalculator.calculateMatchingScore(
                                MIPsUtils.getImageArray(matchedImage),
                                variantImageSuppliers);
                        csr.setGradientAreaGap(negativeScores.getGradientAreaGap());
                        csr.setHighExpressionArea(negativeScores.getHighExpressionArea());
                        LOG.debug("Processing {} - finished calculating negative score between {} and {} in {}ms",
                                cdsMatchesSource, inputQueryMIP, matchedMIP, System.currentTimeMillis() - startGapCalcTime);
                        negativeScore = negativeScores.getScore();
                    } else {
                        csr.setGradientAreaGap(-1);
                        csr.setHighExpressionArea(-1);
                        negativeScore = -1;
                    }
                    return negativeScore;
                }, executor))
                .collect(Collectors.toList());
    }

    private Supplier<ImageArray<?>> createVariantImageSupplier(MIPMetadata mip,
                                                               String variant,
                                                               List<String> variantLocations,
                                                               String variantTypeSuffix,
                                                               String replacedSuffix) {
        return  () -> {
            MIPImage variantImage = CachedMIPsUtils.loadMIP(MIPsUtils.getMIPVariantInfo(
                    mip,
                    variant,
                    variantLocations,
                    nc -> {
                        String suffix = StringUtils.defaultIfBlank(variantTypeSuffix, "");
                        if (StringUtils.isNotBlank(replacedSuffix)) {
                            return StringUtils.replaceIgnoreCase(nc, replacedSuffix, "") + suffix;
                        } else {
                            return nc + suffix;
                        }
                    },
                    null));
            return MIPsUtils.getImageArray(variantImage);
        };
    }

    private CompletableFuture<List<ColorMIPSearchMatchMetadata>> collectNegativeScores(String cdsMatchesSource,
                                                                                       MIPMetadata inputQueryMIP,
                                                                                       List<ColorMIPSearchMatchMetadata> selectedCDSResultsForQueryMIP,
                                                                                       List<CompletableFuture<Long>> negativeScoresComputations,
                                                                                       long startProcessingTime,
                                                                                       Executor executor) {
        LOG.info("Wait for all gradient computations for {} with {} matches from {} to finish to normalize score", inputQueryMIP, selectedCDSResultsForQueryMIP.size(), cdsMatchesSource);
        return CompletableFuture.allOf(negativeScoresComputations.toArray(new CompletableFuture<?>[0]))
                .thenApplyAsync(vr -> {
                    LOG.info("Normalize gradient area scores for {} matches with {} from {}", selectedCDSResultsForQueryMIP.size(), inputQueryMIP, cdsMatchesSource);
                    List<Long> negativeScores = negativeScoresComputations.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());
                    long maxNegativeScore = negativeScores.stream()
                            .max(Long::compare)
                            .orElse(-1L);
                    LOG.info("Max negative score for {} matches with {} from {} -> {}",
                            selectedCDSResultsForQueryMIP.size(), inputQueryMIP, cdsMatchesSource, maxNegativeScore);
                    Integer maxMatchingPixels = selectedCDSResultsForQueryMIP.stream()
                            .map(ColorMIPSearchMatchMetadata::getMatchingPixels)
                            .max(Integer::compare)
                            .orElse(0);
                    LOG.info("Max pixel score for {} matches with {} from {} -> {}",
                            selectedCDSResultsForQueryMIP.size(), inputQueryMIP, cdsMatchesSource, maxMatchingPixels);
                    selectedCDSResultsForQueryMIP.stream()
                            .filter(csr -> csr.getGradientAreaGap() >= 0)
                            .forEach(csr -> {
                                csr.setNormalizedGapScore(GradientAreaGapUtils.calculateNormalizedScore(
                                        csr.getMatchingPixels(),
                                        csr.getGradientAreaGap(),
                                        csr.getHighExpressionArea(),
                                        maxMatchingPixels,
                                        maxNegativeScore
                                ));
                            });
                    LOG.info("Updated normalized score for {} matches with {} from {} after {}s",
                            selectedCDSResultsForQueryMIP.size(), inputQueryMIP, cdsMatchesSource, (System.currentTimeMillis()-startProcessingTime+1000)/1000);
                    return selectedCDSResultsForQueryMIP;
                }, executor);
    }
}
