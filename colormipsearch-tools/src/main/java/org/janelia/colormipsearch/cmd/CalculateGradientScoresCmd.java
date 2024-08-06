package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.datarequests.SortDirection;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to calculate the gradient scores.
 */
class CalculateGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CalculateGradientScoresCmd.class);

    @Parameters(commandDescription = "Calculate gradient scores")
    static class CalculateGradientScoresArgs extends AbstractGradientScoresArgs {
        @Parameter(names = {"--gradscore-parallelism"},
                description = "Specifies the degree of parallelism used for computing the gradscore")
        int gradScoreParallelism;

        @Parameter(names = {"--nBestLines"},
                description = "Specifies the number of the top distinct lines to be used for gradient score")
        int numberOfBestLines;

        @Parameter(names = {"--nBestSamplesPerLine"},
                description = "Specifies the number of the top distinct samples within a line to be used for gradient score")
        int numberOfBestSamplesPerLine;

        @Parameter(names = {"--nBestMatchesPerSample"},
                description = "Number of best matches for each sample to be used for gradient scoring")
        int numberOfBestMatchesPerSample;

        @Parameter(names = {"--process-partitions-concurrently"},
                description = "If set, process mask partitions concurrently",
                arity = 0)
        boolean processPartitionsConcurrently = false;

        CalculateGradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final CalculateGradientScoresArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final ObjectMapper mapper;

    CalculateGradientScoresCmd(String commandName,
                               CommonArgs commonArgs,
                               Supplier<Long> cacheSizeSupplier) {
        super(commandName);
        this.args = new CalculateGradientScoresArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    CalculateGradientScoresArgs getArgs() {
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
        long startTime = System.currentTimeMillis();
        ImageRegionDefinition excludedRegions = args.getRegionGeneratorForTextLabels();
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                args.mirrorMask,
                args.negativeRadius,
                args.borderSize,
                loadQueryROIMask(args.queryROIMaskName),
                excludedRegions
        );
        NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader = getCDMatchesReader();
        Collection<String> matchesMasksToProcess = cdMatchesReader.listMatchesLocations(
                args.masksLibraries.stream()
                        .map(larg -> new DataSourceParam()
                                .setAlignmentSpace(args.alignmentSpace)
                                .addLibrary(larg.input)
                                .addNames(args.masksPublishedNames)
                                .addMipIDs(args.masksMIPIDs)
                                .addDatasets(args.maskDatasets)
                                .addTags(args.maskTags)
                                .addAnnotations(args.maskAnnotations)
                                .addExcludedAnnotations(args.excludedMaskAnnotations)
                                .addProcessingTags(args.getMasksProcessingTags())
                                .setOffset(larg.offset)
                                .setSize(larg.length))
                        .collect(Collectors.toList()));
        int size = matchesMasksToProcess.size();
        Executor executor = CmdUtils.createCmdExecutor(args.commonArgs);
        Stream<Map.Entry<Integer, List<String>>> masksPartitionedStream;
        // partition masks
        if (args.processPartitionsConcurrently) {
            masksPartitionedStream = ItemsHandling.partitionCollection(matchesMasksToProcess, args.processingPartitionSize)
                    .entrySet()
                    .parallelStream();
        } else {
            masksPartitionedStream = ItemsHandling.partitionCollection(matchesMasksToProcess, args.processingPartitionSize)
                    .entrySet()
                    .stream();
        }
        masksPartitionedStream.forEach(indexedPartition -> {
            int partitionId = indexedPartition.getKey(); // unbox it
            List<String> partionMasks = indexedPartition.getValue();
            processMasks(
                    partionMasks,
                    cdMatchesReader,
                    gradScoreAlgorithmProvider,
                    executor,
                    String.format("Partition %d", partitionId)
            );
        });
        LOG.info("Finished calculating gradient scores for {} items in {}s - memory usage {}M out of {}M",
                size,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private void processMasks(List<String> masksIds,
                              NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader,
                              ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
                              Executor executor,
                              String processingContext) {
        LOG.info("Start {} - process {} masks", processingContext, masksIds.size());
        long startProcessingPartitionTime = System.currentTimeMillis();
        long updatedMatches = 0;
        for (String maskId : masksIds) {
            updatedMatches += processMask(maskId, cdMatchesReader, gradScoreAlgorithmProvider, executor, processingContext);
        }
        LOG.info("Finished {} - completed {} masks, updated {} matches in {}s - memory usage {}M out of {}M",
                processingContext,
                masksIds.size(),
                updatedMatches,
                (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private long processMask(String maskId,
                             NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader,
                             ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
                             Executor executor,
                             String processingContext) {
        // read all matches for the current mask
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesForMask = getCDMatchesForMask(cdMatchesReader, maskId);
        long nPublishedNames = cdMatchesForMask.stream()
                .map(cdm -> cdm.getMatchedImage().getPublishedName())
                .distinct()
                .count();
        long nSourceSamples = cdMatchesForMask.stream()
                .map(cdm -> cdm.getMatchedImage().getSourceRefId())
                .distinct()
                .count();
        // calculate the grad scores
        LOG.info("{} - calculate grad scores for {} matches ({}/{} published names/source samples) of {} - memory usage {}M out of {}M",
                processingContext,
                cdMatchesForMask.size(),
                nPublishedNames,
                nSourceSamples,
                maskId,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesWithGradScores = calculateGradientScores(
                gradScoreAlgorithmProvider,
                cdMatchesForMask,
                args.gradScoreParallelism,
                executor);
        LOG.info("{} - completed grad scores for {} matches of {} - memory usage {}M out of {}M",
                processingContext,
                cdMatchesWithGradScores.size(),
                maskId,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        long writtenUpdates = updateCDMatches(cdMatchesWithGradScores, getCDMatchesWriter());
        LOG.info("{} - updated {} grad scores for {} matches of {} - memory usage {}M out of {}M",
                processingContext,
                writtenUpdates,
                cdMatchesWithGradScores.size(),
                maskId,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        if (StringUtils.isNotBlank(args.processingTag)) {
            long updatesWithProcessedTag = updateProcessingTag(cdMatchesForMask);
            LOG.info("{} - set processing tag {} for {} mips - memory usage {}M out of {}M",
                    processingContext,
                    args.getProcessingTag(),
                    updatesWithProcessedTag,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (Runtime.getRuntime().totalMemory() / _1M));
        }
        System.gc(); // explicitly garbage collect
        return writtenUpdates;
    }

    /**
     * The ROI mask is typically the hemibrain mask that should be applied when the color depth search is done from LM to EM.
     *
     * @param queryROIMask the location of the ROI mask
     * @return
     */
    private ImageArray<?> loadQueryROIMask(String queryROIMask) {
        if (StringUtils.isBlank(queryROIMask)) {
            return null;
        } else {
            return NeuronMIPUtils.loadImageFromFileData(FileData.fromString(queryROIMask));
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesReader<CDMatchEntity<M, T>> getCDMatchesReader() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            DaosProvider daosProvider = getDaosProvider();
            return new DBNeuronMatchesReader<>(
                    daosProvider.getNeuronMetadataDao(),
                    daosProvider.getCDMatchesDao(),
                    "mipId");
        } else {
            return new JSONNeuronMatchesReader<>(mapper);
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesWriter<CDMatchEntity<M, T>> getCDMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesWriter<>(getDaosProvider().getCDMatchesDao());
        } else {
            return new JSONNeuronMatchesWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    AbstractNeuronEntity::getMipId, // group results by neuron MIP ID
                    Comparator.comparingDouble(m -> -(((CDMatchEntity<?, ?>) m).getNormalizedScore())), // descending order by matching pixels
                    args.getOutputDir(),
                    null
            );
        }
    }

    private CDMIPsWriter getCDMipsWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao());
        } else {
            return null;
        }
    }

    /**
     * The method calculates and updates the gradient scores for all color depth matches of the given mask MIP ID.
     *
     * @param gradScoreAlgorithmProvider grad score algorithm provider
     * @param cdMatches                  color depth matches for which the grad score will be computed
     * @param gradScoreParallelism       the degree of parallelism used for calculating the grad score(s)
     * @param executor                   task executor
     * @param <M>                        mask type
     * @param <T>                        target type
     */
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> calculateGradientScores(
            ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
            List<CDMatchEntity<M, T>> cdMatches,
            int gradScoreParallelism,
            Executor executor) {
        try {
            // group the matches by the mask input file - this is because we do not want to mix FL and non-FL neuron images for example
            List<GroupedMatchedEntities<M, T, CDMatchEntity<M, T>>> selectedMatchesGroupedByInput =
                    MatchEntitiesGrouping.simpleGroupByMaskFields(
                            cdMatches,
                            Arrays.asList(
                                    AbstractNeuronEntity::getMipId,
                                    m -> m.getComputeFileName(ComputeFileType.InputColorDepthImage)
                            )
                    );
            List<CompletableFuture<List<CDMatchEntity<M, T>>>> gradScoreComputations = selectedMatchesGroupedByInput.stream()
                    .flatMap(selectedMaskMatches -> startGradScoreComputations(
                            selectedMaskMatches.getKey(),
                            selectedMaskMatches.getItems(),
                            gradScoreAlgorithmProvider,
                            gradScoreParallelism,
                            executor
                    ).stream())
                    .collect(Collectors.toList());
            // wait for all computation to finish
            CompletableFuture<?>[] allGradScoreComputations = gradScoreComputations.toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(allGradScoreComputations).join();

            return gradScoreComputations.stream()
                    .map(CompletableFuture::join)
                    .flatMap(Collection::stream)
                    .filter(CDMatchEntity::hasGradScore)
                    .collect(Collectors.toList());
        } finally {
            System.gc(); // force gc
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches,
                                                                                                  NeuronMatchesWriter<CDMatchEntity<M, T>> matchesWriter) {
        // update normalized scores
        updateNormalizedScores(cdMatches);
        // then write them down
        return matchesWriter.writeUpdates(
                cdMatches,
                Arrays.asList(
                        m -> ImmutablePair.of("gradientAreaGap", m.getGradientAreaGap()),
                        m -> ImmutablePair.of("highExpressionArea", m.getHighExpressionArea()),
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore()),
                        m -> ImmutablePair.of("updatedDate", new Date())
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateProcessingTag(List<CDMatchEntity<M, T>> cdMatches) {
        CDMIPsWriter cdmipsWriter = getCDMipsWriter();
        if (cdmipsWriter != null) {
            Set<String> processingTags = Collections.singleton(args.getProcessingTag());
            Set<M> masksToUpdate = cdMatches.stream()
                    .map(AbstractMatchEntity::getMaskImage).collect(Collectors.toSet());
            Set<T> targetsToUpdate = cdMatches.stream()
                    .map(AbstractMatchEntity::getMatchedImage).collect(Collectors.toSet());
            cdmipsWriter.addProcessingTags(masksToUpdate, ProcessingType.GradientScore, processingTags);
            cdmipsWriter.addProcessingTags(targetsToUpdate, ProcessingType.GradientScore, processingTags);
            return masksToUpdate.size() + targetsToUpdate.size();
        } else {
            return 0;
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMask(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, String maskCDMipId) {
        LOG.info("Read all color depth matches for {}", maskCDMipId);
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (args.pctPositivePixels > 0) {
            neuronsMatchScoresFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        List<CDMatchEntity<M, T>> allCDMatches = cdsMatchesReader.readMatchesByMask(
                args.alignmentSpace,
                new DataSourceParam()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addMipID(maskCDMipId)
                        .addDatasets(args.maskDatasets)
                        .addTags(args.maskTags)
                        .addAnnotations(args.maskAnnotations)
                        .addExcludedAnnotations(args.excludedMaskAnnotations),
                new DataSourceParam()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addLibraries(args.targetsLibraries)
                        .addNames(args.targetsPublishedNames)
                        .addMipIDs(args.targetsMIPIDs)
                        .addDatasets(args.targetDatasets)
                        .addTags(args.targetTags)
                        .addAnnotations(args.targetAnnotations)
                        .addExcludedAnnotations(args.excludedTargetAnnotations)
                        .addProcessingTags(args.getTargetsProcessingTags()),
                /* matchTags */args.matchTags,
                /* matchExcludedTags */null,
                neuronsMatchScoresFilter,
                Collections.singletonList(
                        new SortCriteria("normalizedScore", SortDirection.DESC)
                ));
        // select best matches to process
        LOG.info("Select best color depth matches for {} out of {} total matches", maskCDMipId, allCDMatches.size());
        return ColorMIPProcessUtils.selectBestMatches(
                allCDMatches,
                args.numberOfBestLines,
                args.numberOfBestSamplesPerLine,
                args.numberOfBestMatchesPerSample
        );
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CompletableFuture<List<CDMatchEntity<M, T>>>> startGradScoreComputations(M mask,
                                                                                List<CDMatchEntity<M, T>> selectedMatches,
                                                                                ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
                                                                                int gradScoreParallelism,
                                                                                Executor executor) {
        if (CollectionUtils.isEmpty(selectedMatches)) {
            LOG.error("No matches were selected for {}", mask);
            return Collections.emptyList();
        }
        LOG.info("Prepare gradient score computations for {} with {} matches", mask, selectedMatches.size());
        LOG.info("Load query image {}", mask);
        NeuronMIP<M> maskImage = NeuronMIPUtils.loadComputeFile(mask, ComputeFileType.InputColorDepthImage);
        if (NeuronMIPUtils.hasNoImageArray(maskImage)) {
            LOG.error("No image found for {}", mask);
            return Collections.emptyList();
        }
        ColorDepthSearchAlgorithm<ShapeMatchScore> gradScoreAlgorithm =
                gradScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        maskImage.getImageArray(),
                        args.maskThreshold,
                        args.borderSize);
        Set<ComputeFileType> requiredVariantTypes = gradScoreAlgorithm.getRequiredTargetVariantTypes();
        // partition size is the inverse of the parallelism (if zero process all in parallel)
        int selectedMatchesPartitionSize = gradScoreParallelism > 0
                ? Math.max(selectedMatches.size() / gradScoreParallelism, 1)
                : 1;
        Map<Integer, List<CDMatchEntity<M, T>>> selectedMatchesParallelPartitions =
                ItemsHandling.partitionCollection(selectedMatches, selectedMatchesPartitionSize);
        LOG.info("Partition {} selected matches of {} in {} partitions of size {}",
                selectedMatches.size(), mask, selectedMatchesParallelPartitions.size(), selectedMatchesPartitionSize);
        return selectedMatchesParallelPartitions
                .values().stream()
                .map(cdsMatches -> CompletableFuture.supplyAsync(() -> {
                    for (CDMatchEntity<M, T> cdsMatch : cdsMatches) {
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
                            cdsMatch.setHighExpressionArea(-1L);
                        }
                    }
                    System.gc();
                    return cdsMatches;
                }, executor))
                .collect(Collectors.toList());
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void updateNormalizedScores(List<CDMatchEntity<M, T>> cdMatches) {
        // get max scores for normalization
        CombinedMatchScore maxScores = cdMatches.stream()
                .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                .reduce(new CombinedMatchScore(-1, -1L),
                        (s1, s2) -> new CombinedMatchScore(
                                Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                Math.max(s1.getGradScore(), s2.getGradScore())));
        // update normalized score
        cdMatches.forEach(m -> m.setNormalizedScore((float) GradientAreaGapUtils.calculateNormalizedScore(
                m.getMatchingPixels(),
                m.getGradientAreaGap(),
                m.getHighExpressionArea(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

}
