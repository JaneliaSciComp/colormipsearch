package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProviderFactory;
import org.janelia.colormipsearch.cds.CombinedMatchScore;
import org.janelia.colormipsearch.cds.GradientAreaGapUtils;
import org.janelia.colormipsearch.cds.ShapeMatchScore;
import org.janelia.colormipsearch.cmd.cdsprocess.ColorMIPProcessUtils;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesUpdater;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesUpdater;
import org.janelia.colormipsearch.dataio.fs.JSONCDSMatchesReader;
import org.janelia.colormipsearch.dataio.fs.JSONCDSMatchesUpdater;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDMatch;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronMetadata;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronMetadata;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchResultsGrouping;
import org.janelia.colormipsearch.results.ResultMatches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalculateGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CalculateGradientScoresCmd.class);

    @Parameters(commandDescription = "Color depth search for a batch of MIPs")
    static class GradientScoresArgs extends AbstractColorDepthMatchArgs {

        @Parameter(names = {"--matchesDir", "-md"}, required = true, variableArity = true,
                converter = ListArg.ListArgConverter.class,
                description = "Argument containing matches resulted from a color depth search process. " +
                        "This can be a directory of JSON files, a list of specific JSON files or some `DB selector`")
        List<ListArg> matches;

        @Parameter(names = {"--nBestLines"},
                description = "Specifies the number of the top distinct lines to be used for gradient score")
        int numberOfBestLines;

        @Parameter(names = {"--nBestSamplesPerLine"},
                description = "Specifies the number of the top distinct samples within a line to be used for gradient score")
        int numberOfBestSamplesPerLine;

        @Parameter(names = {"--nBestMatchesPerSample"},
                description = "Number of best matches for each sample to be used for gradient scoring")
        int numberOfBestMatchesPerSample;

        public GradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final GradientScoresArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final ObjectMapper mapper;

    public CalculateGradientScoresCmd(String commandName,
                                      CommonArgs commonArgs,
                                      Supplier<Long> cacheSizeSupplier) {
        super(commandName);
        this.args = new GradientScoresArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    GradientScoresArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // initialize the cache
        CachedMIPsUtils.initializeCache(cacheSizeSupplier.get());
        // run gradient scoring
        calculateAllGradientScores();
    }

    private void calculateAllGradientScores() {
        ImageRegionDefinition excludedRegions = args.getRegionGeneratorForTextLabels();
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                args.mirrorMask,
                args.negativeRadius,
                args.borderSize,
                loadQueryROIMask(args.queryROIMaskName),
                excludedRegions
        );
        NeuronMatchesReader<EMNeuronMetadata, LMNeuronMetadata, CDMatch<EMNeuronMetadata, LMNeuronMetadata>> cdMatchesReader = getCDMatchesReader();

        long startTime = System.currentTimeMillis();
        List<String> itemsToProcess = cdMatchesReader.listMatchesLocations(args.matches.stream().map(ListArg::asDataSourceParam).collect(Collectors.toList()));
        int size = itemsToProcess.size();
        Executor executor = CmdUtils.createCmdExecutor(args.commonArgs);
        ItemsHandling.partitionCollection(itemsToProcess, args.processingPartitionSize).stream().parallel()
                .forEach(partititionItems -> {
                    long startProcessingPartitionTime = System.currentTimeMillis();
                    partititionItems.forEach(toProcess -> {
                        calculateAndUpdateGradientScores(
                                gradScoreAlgorithmProvider,
                                cdMatchesReader,
                                toProcess,
                                executor
                        );
                    });
                    LOG.info("Finished a batch of {} in {}s - memory usage {}M out of {}M",
                            partititionItems.size(),
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                });
        LOG.info("Finished calculating gradient scores for {} items in {}s - memory usage {}M out of {}M",
                size,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private ImageArray<?> loadQueryROIMask(String queryROIMask) {
        if (StringUtils.isBlank(queryROIMask)) {
            return null;
        } else {
            return NeuronMIPUtils.loadImageFromFileData(FileData.fromString(queryROIMask));
        }
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> NeuronMatchesReader<M, T, CDMatch<M, T>> getCDMatchesReader() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesReader<>(getConfig());
        } else {
            return new JSONCDSMatchesReader<>(mapper);
        }
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> NeuronMatchesUpdater<M, T, CDMatch<M, T>> getCDMatchesUpdater() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesUpdater<>(getConfig());
        } else {
            return new JSONCDSMatchesUpdater<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    args.getOutputDir()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> void calculateAndUpdateGradientScores(
            ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
            NeuronMatchesReader<M, T, CDMatch<M, T>> cdsMatchesReader,
            String cdsMatchesSource,
            Executor executor) {
        LOG.info("Read color depth matches from {}", cdsMatchesSource);
        NeuronsMatchFilter<CDMatch<M, T>> neuronsMatchFilter = new NeuronsMatchFilter<>();
        neuronsMatchFilter.setMatchType(CDMatch.class.getName());
        if (args.pctPositivePixels > 0) {
            neuronsMatchFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        List<CDMatch<M, T>> allCDMatches = cdsMatchesReader.readMatches(cdsMatchesSource, neuronsMatchFilter);
        // select best matches to process
        List<CDMatch<M, T>> selectedMatches = ColorMIPProcessUtils.selectBestMatches(
                allCDMatches,
                args.numberOfBestLines,
                args.numberOfBestSamplesPerLine,
                args.numberOfBestMatchesPerSample
        );
        // group the matches by the mask input file - this is because we do not want to mix FL and non-FL neuron images for example
        List<ResultMatches<M, T, CDMatch<M, T>>> selectedMatchesGroupedByInput =
                MatchResultsGrouping.simpleGroupByMaskFields(
                        selectedMatches,
                        Arrays.asList(
                                AbstractNeuronMetadata::getId,
                                m -> m.getComputeFileName(ComputeFileType.InputColorDepthImage)
                        )
                );
        List<CompletableFuture<CDMatch<M, T>>> gradScoreComputations = selectedMatchesGroupedByInput.stream()
                .flatMap(selectedMaskMatches -> runGradScoreComputations(
                        selectedMaskMatches.getKey(),
                        selectedMaskMatches.getItems(),
                        gradScoreAlgorithmProvider,
                        executor
                ).stream())
                .collect(Collectors.toList());
        // wait for all computation to finish
        List<CDMatch<M, T>> matchesWithGradScores = gradScoreComputations.stream()
                .map(CompletableFuture::join)
                .filter(CDMatch::hasGradScore)
                .collect(Collectors.toList());

        updateNormalizedScores(matchesWithGradScores);

        NeuronMatchesUpdater<M, T, CDMatch<M, T>> updatesWriter = getCDMatchesUpdater();
        updatesWriter.writeUpdates(
                matchesWithGradScores,
                Arrays.asList(
                        m -> ImmutablePair.of("gradientAreaGap", m.getGradientAreaGap()),
                        m -> ImmutablePair.of("highExpressionArea", m.getHighExpressionArea()),
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore())
                ));
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata>
    List<CompletableFuture<CDMatch<M, T>>> runGradScoreComputations(M mask,
                                                                    List<CDMatch<M, T>> selectedMatches,
                                                                    ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
                                                                    Executor executor) {
        LOG.info("Prepare gradient score computations for {} with {} matches", mask, selectedMatches.size());
        LOG.info("Load query image {}", mask);
        NeuronMIP<M> maskImage = NeuronMIPUtils.loadComputeFile(mask, ComputeFileType.InputColorDepthImage);
        if (NeuronMIPUtils.hasNoImageArray(maskImage) || CollectionUtils.isEmpty(selectedMatches)) {
            LOG.error("No image found for {}", mask);
            return Collections.emptyList();
        }
        ColorDepthSearchAlgorithm<ShapeMatchScore> gradScoreAlgorithm =
                gradScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        maskImage.getImageArray(),
                        args.maskThreshold,
                        args.borderSize);
        Set<ComputeFileType> requiredVariantTypes = gradScoreAlgorithm.getRequiredTargetVariantTypes();
        return selectedMatches.stream()
                .map(cdsMatch -> CompletableFuture.supplyAsync(() -> {
                            long startCalcTime = System.currentTimeMillis();
                            T matchedTarget = cdsMatch.getMatchedImage();
                            NeuronMIP<T> matchedTargetImage = CachedMIPsUtils.loadMIP(matchedTarget, ComputeFileType.InputColorDepthImage);
                            if (NeuronMIPUtils.hasImageArray(matchedTargetImage)) {
                                LOG.debug("Calculate grad score between {} and {}",
                                        cdsMatch.getMaskImage(), cdsMatch.getMatchedImage());
                                ShapeMatchScore gradScore = gradScoreAlgorithm.calculateMatchingScore(
                                        matchedTargetImage.getImageArray(),
                                        NeuronMIPUtils.getImageLoaders(
                                                matchedTarget,
                                                requiredVariantTypes,
                                                (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadMIP(n, cft))
                                        )
                                );
                                cdsMatch.setGradientAreaGap(gradScore.getGradientAreaGap());
                                cdsMatch.setHighExpressionArea(gradScore.getHighExpressionArea());
                                cdsMatch.setNormalizedScore(gradScore.getNormalizedScore());
                                LOG.debug("Finished calculating negative score between {} and {} in {}ms",
                                        cdsMatch.getMaskImage(), cdsMatch.getMatchedImage(), System.currentTimeMillis() - startCalcTime);
                            } else {
                                cdsMatch.setGradientAreaGap(-1L);
                            }
                            return cdsMatch;
                        },
                        executor))
                .collect(Collectors.toList());
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> void updateNormalizedScores(List<CDMatch<M, T>> CDMatches) {
        // get max scores for normalization
        CombinedMatchScore maxScores = CDMatches.stream()
                .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                .reduce(new CombinedMatchScore(-1, -1L),
                        (s1, s2) -> new CombinedMatchScore(
                                Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                Math.max(s1.getGradScore(), s2.getGradScore())));
        // update normalized score
        CDMatches.forEach(m -> m.setNormalizedScore((float) GradientAreaGapUtils.calculateNormalizedScore(
                m.getMatchingPixels(),
                m.getGradientAreaGap(),
                m.getHighExpressionArea(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

}
