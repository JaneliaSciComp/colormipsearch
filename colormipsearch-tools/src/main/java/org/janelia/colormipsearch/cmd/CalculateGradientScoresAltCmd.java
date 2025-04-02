package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Preconditions;

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
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Command to calculate the gradient scores.
 */
class CalculateGradientScoresAltCmd extends AbstractCmd {

    private final static int LOW_MEMORY_PERC_THRESHOLD = 10;
    private static final Logger LOG = LoggerFactory.getLogger(CalculateGradientScoresAltCmd.class);

    @Parameters(commandDescription = "Calculate gradient scores")
    static class CalculateGradientScoresArgs extends AbstractGradientScoresArgs {
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

        @Parameter(names = {"--use-bidirectional-matching"},
                description = "Use bidirectional matching",
                arity = 0)
        boolean useBidirectionalMatching = false;

        CalculateGradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final CalculateGradientScoresArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final ObjectMapper mapper;

    CalculateGradientScoresAltCmd(String commandName,
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
        List<CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> scoredMatches = startAllGradientScores();
        LOG.info("Finished calculating gradient scores for {} items in {}s - memory usage {}M out of {}M",
                scoredMatches.size(),
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        long startUpdateTime = System.currentTimeMillis();
        long updated = updateCDMatches(scoredMatches);
        LOG.info("Finished updating gradient scores for {} items ({} updated) in {}s - memory usage {}M out of {}M",
                scoredMatches.size(), updated,
                (System.currentTimeMillis() - startUpdateTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));

        Set<AbstractNeuronEntity> mipsToUpdate = scoredMatches.stream()
                .flatMap(m -> Stream.of(m.getMaskImage(), m.getMatchedImage()))
                .collect(Collectors.toSet());
        CDMIPsWriter cdmipsWriter = getCDMipsWriter();
        cdmipsWriter.addProcessingTags(mipsToUpdate, ProcessingType.GradientScore, Collections.singleton(args.getProcessingTag()));
        LOG.info("Annotated {} mips with {} in {}s - memory usage {}M out of {}M",
                mipsToUpdate.size(),
                ProcessingType.GradientScore,
                (System.currentTimeMillis() - startUpdateTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    @Nonnull
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> startAllGradientScores() {
        ImageRegionDefinition excludedRegions = args.getRegionGeneratorForTextLabels();
        NeuronMatchesReader<CDMatchEntity<M, T>> cdMatchesReader = getCDMatchesReader();
        Collection<String> maskIdsToProcess = cdMatchesReader.listMatchesLocations(
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
        int size = maskIdsToProcess.size();
        LOG.info("Collect matches to calculate all gradient scores for {} masks", size);
        List<CDMatchEntity<M, T>> allMatchesToBeScored = maskIdsToProcess.stream().parallel()
                .flatMap(maskId -> getCDMatchesForMask(cdMatchesReader, maskId).stream())
                .collect(Collectors.toList());
        LOG.info("Prepare to calculate gradient scores for {} masks with a total of {} matches", size, allMatchesToBeScored.size());
        if (CollectionUtils.isEmpty(allMatchesToBeScored)) {
            return Collections.emptyList(); // nothing to do
        }
        int bufferingSize = args.processingPartitionSize > 0
                ? args.processingPartitionSize
                : 1;
        LOG.info("Split work into {} partitions of size {} - memory usage {}M out of {}M",
                allMatchesToBeScored.size() / bufferingSize + 1, bufferingSize,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        ExecutorService executorService = CmdUtils.createCmdExecutor(args.commonArgs);
        try {
            Scheduler scheduler = Schedulers.fromExecutorService(executorService);

            ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                    args.mirrorMask,
                    args.negativeRadius,
                    args.borderSize,
                    loadQueryROIMask(args.queryROIMaskName),
                    excludedRegions
            );
            List<CDMatchEntity<M, T>> scoredMatches = Flux.fromIterable(allMatchesToBeScored)
                    .buffer(bufferingSize)
                    .parallel()
                    .runOn(scheduler)
                    .flatMap(cdMatches -> calculateGradientScores(shapeScoreAlgorithmProvider, cdMatches))
                    .sequential()
                    .collectList()
                    .block();
            Preconditions.checkArgument(scoredMatches != null);
            return scoredMatches;
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * The ROI mask is typically the hemibrain mask that should be applied when the color depth search is done from LM to EM.
     *
     * @param queryROIMask the location of the ROI mask
     * @return the image array for the ROI mask
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
     * @param shapeScoreAlgorithmProvider shape score algorithm provider
     * @param cdMatches                   color depth matches for which the grad score will be computed
     * @param <M>                         mask type
     * @param <T>                         target type
     */
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    Flux<CDMatchEntity<M, T>> calculateGradientScores(ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider,
                                                      List<CDMatchEntity<M, T>> cdMatches) {
        // group the matches by the mask input file - this is because we do not want to mix FL and non-FL neuron images for example
        List<GroupedMatchedEntities<M, T, CDMatchEntity<M, T>>> selectedMatchesGroupedByInput =
                MatchEntitiesGrouping.simpleGroupByMaskFields(
                        cdMatches,
                        Arrays.asList(
                                AbstractNeuronEntity::getMipId,
                                m -> m.getComputeFileName(ComputeFileType.InputColorDepthImage)
                        )
                );
        return Flux.fromIterable(selectedMatchesGroupedByInput)
                .map(selectedMaskMatches -> runGradScoreComputations(
                        selectedMaskMatches.getKey(),
                        selectedMaskMatches.getItems(),
                        shapeScoreAlgorithmProvider))
                .flatMap(Flux::fromIterable)
                ;
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        // update normalized scores
        updateNormalizedScores(cdMatches);
        // then write them down
        NeuronMatchesWriter<CDMatchEntity<M, T>> cdMatchesWriter = getCDMatchesWriter();
        return cdMatchesWriter.writeUpdates(
                cdMatches,
                Arrays.asList(
                        m -> ImmutablePair.of("bidirectionalAreaGap", m.getBidirectionalAreaGap()),
                        m -> ImmutablePair.of("gradientAreaGap", m.getGradientAreaGap()),
                        m -> ImmutablePair.of("highExpressionArea", m.getHighExpressionArea()),
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore()),
                        m -> ImmutablePair.of("updatedDate", new Date())
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateProcessingTag(List<CDMatchEntity<M, T>> cdMatches, CDMIPsWriter cdmipsWriter) {
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
        List<CDMatchEntity<M, T>> bestMatches = ColorMIPProcessUtils.selectBestMatches(
                allCDMatches,
                args.numberOfBestLines,
                args.numberOfBestSamplesPerLine,
                args.numberOfBestMatchesPerSample
        );
        LOG.info("Selected {} best color depth matches for {} out of {} total matches", bestMatches.size(), maskCDMipId, allCDMatches.size());
        return bestMatches;
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> runGradScoreComputations(M mask,
                                                       List<CDMatchEntity<M, T>> selectedMatches,
                                                       ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider) {
        long startTime = System.currentTimeMillis();
        MDC.put("maskId", mask.getMipId() + "/" + mask.getEntityId());
        try {
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
            ColorDepthSearchAlgorithm<ShapeMatchScore> shapeScoreAlgorithm =
                    shapeScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                            maskImage.getImageArray(),
                            args.maskThreshold,
                            args.borderSize);
            Set<ComputeFileType> requiredVariantTypes = shapeScoreAlgorithm.getRequiredTargetVariantTypes();
            return Flux.fromIterable(selectedMatches)
                    .doOnNext(cdsMatch -> {
                        long startCalcTime = System.currentTimeMillis();
                        T matchedTarget = cdsMatch.getMatchedImage();
                        MDC.put("targetId", matchedTarget.getMipId() + "/" + matchedTarget.getEntityId());
                        NeuronMIP<T> matchedTargetImage = CachedMIPsUtils.loadMIP(matchedTarget, ComputeFileType.InputColorDepthImage);
                        if (NeuronMIPUtils.hasImageArray(matchedTargetImage)) {
                            LOG.debug("Calculate grad score between {} and {}",
                                    cdsMatch.getMaskImage(), cdsMatch.getMatchedImage());
                            ShapeMatchScore gradScore = shapeScoreAlgorithm.calculateMatchingScore(
                                    matchedTargetImage.getImageArray(),
                                    NeuronMIPUtils.getImageLoaders(
                                            matchedTarget,
                                            requiredVariantTypes,
                                            (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadMIP(n, cft))
                                    )
                            );
                            cdsMatch.setBidirectionalAreaGap(gradScore.getBidirectionalAreaGap());
                            cdsMatch.setGradientAreaGap(gradScore.getGradientAreaGap());
                            cdsMatch.setHighExpressionArea(gradScore.getHighExpressionArea());
                            cdsMatch.setNormalizedScore(gradScore.getNormalizedScore());
                            LOG.debug("Negative score between {} and {} is {} - computed in {}ms",
                                    cdsMatch.getMaskImage(), cdsMatch.getMatchedImage(),
                                    gradScore,
                                    System.currentTimeMillis() - startCalcTime);
                        } else {
                            LOG.info("No image found for {}", matchedTarget);
                            cdsMatch.setBidirectionalAreaGap(-1L);
                            cdsMatch.setGradientAreaGap(-1L);
                            cdsMatch.setHighExpressionArea(-1L);
                        }
                        MDC.remove("targetId");
                    })
                    .filter(CDMatchEntity::hasGradScore)
                    .collectList()
                    .block()
                    ;
        } finally {
            LOG.info("Finished gradient score computations for {} with {} matches in {}s - memory usage {}M out of {}M",
                    mask, selectedMatches.size(),
                    (System.currentTimeMillis() - startTime)/1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (Runtime.getRuntime().totalMemory() / _1M));
            checkMemoryUsage();
            MDC.remove("maskId");
        }
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
                m.getGradScore(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

    private void checkMemoryUsage() {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long threshold = maxMemory / LOW_MEMORY_PERC_THRESHOLD; // 10% of max memory
        if (freeMemory < threshold) {
            LOG.warn("Low memory detected: free memory: {} bytes, max memory: {} bytes", freeMemory, maxMemory);
            System.gc();
        }
    }
}
