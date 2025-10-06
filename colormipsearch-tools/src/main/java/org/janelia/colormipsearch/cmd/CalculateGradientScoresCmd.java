package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.commons.lang3.tuple.Pair;
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
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.GroupedItems;
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
class CalculateGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CalculateGradientScoresCmd.class);

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

        // this parameter is not used but it's here for future cmd line compatibility
        // when we start supporting 3d bidirectional shape matching
        @Parameter(names = {"--use-bidirectional-matching"},
                description = "Use bidirectional matching",
                arity = 0)
        boolean useBidirectionalMatching = false;

        CalculateGradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    static class GeneratorState<E> {
        final AtomicInteger currentIndex;
        final List<E> elems;

        GeneratorState(List<E> elems) {
            this.currentIndex = new AtomicInteger(0);
            this.elems = elems;
        }

        boolean hasNext() {
            return currentIndex.get() < elems.size();
        }

        E next() {
            int index = currentIndex.getAndIncrement();
            if (index < elems.size()) {
                LOG.debug("Serving element {} out of {}", index + 1, elems.size());
                return elems.get(index);
            } else {
                return null;
            }
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
        List<CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> allScoredMatches = startAllGradientScores();
        LOG.info("Finished calculating gradient scores (unnormalized) for {} items in {}s - memory usage {}M out of {}M",
                allScoredMatches.size(),
                (System.currentTimeMillis() - startTime) / 1000.,
                (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (maxMemory / _1M));
        long startUpdateTime = System.currentTimeMillis();
        normalizeScores(allScoredMatches);
        // update matches in the storage
        long updated = updateCDMatches(allScoredMatches);
        LOG.info("Finished updating gradient scores (normalized) for {} items ({} updated) in {}s - memory usage {}M out of {}M",
                allScoredMatches.size(), updated,
                (System.currentTimeMillis() - startUpdateTime) / 1000.,
                (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (maxMemory / _1M));

        Set<AbstractNeuronEntity> mipsToUpdate = allScoredMatches.stream()
                .flatMap(m -> Stream.of(m.getMaskImage(), m.getMatchedImage()))
                .collect(Collectors.toSet());
        CDMIPsWriter cdmipsWriter = getCDMipsWriter();
        if (cdmipsWriter != null) {
            long updatedMips = cdmipsWriter.addProcessingTags(mipsToUpdate, ProcessingType.GradientScore, Collections.singleton(args.getProcessingTag()));
            LOG.info("Annotated {} ({}) mips with {} in {}s - memory usage {}M out of {}M",
                    mipsToUpdate.size(), updatedMips,
                    ProcessingType.GradientScore,
                    (System.currentTimeMillis() - startUpdateTime) / 1000.,
                    (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (maxMemory / _1M));
        }
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

        LOG.info("Collect matches to calculate all gradient scores for {} masks: {}", size, getShortenedName(maskIdsToProcess, 10, m -> m));
        List<GroupedItems<M, CDMatchEntity<M, T>>> matchesToBeScoredGroupedByMask = maskIdsToProcess.stream().parallel()
                .flatMap(maskId -> getCDMatchesForMaskMipID(cdMatchesReader, maskId).stream())
                .collect(Collectors.toList());

        long nMatches = matchesToBeScoredGroupedByMask.stream()
                .mapToLong(gm -> gm.getItems().size())
                .sum();

        LOG.info("Prepare to calculate {} gradient scores for {} grouped matches", nMatches, matchesToBeScoredGroupedByMask.size());
        if (CollectionUtils.isEmpty(matchesToBeScoredGroupedByMask)) {
            return Collections.emptyList(); // nothing to do
        }
        int bufferingSize = args.processingPartitionSize > 0
                ? args.processingPartitionSize
                : 1;

        LOG.info("Split work into {} partitions of size {} - memory usage {}M out of {}M",
                nMatches / bufferingSize + 1, bufferingSize,
                (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (maxMemory / _1M));
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

            List<CDMatchEntity<M, T>> allScoredMatches = Flux.fromIterable(matchesToBeScoredGroupedByMask)
                    .flatMap(maskMatches -> createGradScoreComputationsForMask(maskMatches.getKey(), maskMatches.getItems(), shapeScoreAlgorithmProvider))
                    .buffer(bufferingSize) // create processing partitions - all partitions will be dispatched concurrently and items from a partition will be processed sequentially
                    .parallel(CmdUtils.getTaskConcurrency(args.commonArgs))
                    .runOn(scheduler)
                    .flatMap(this::runGradientScoreComputations)
                    .sequential()
                    .collectList()
                    .block();

            checkMemoryUsage();
            Preconditions.checkArgument(allScoredMatches != null);
            return allScoredMatches;
        } finally {
            executorService.shutdown();
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<GroupedItems<M, CDMatchEntity<M, T>>> getCDMatchesForMaskMipID(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, String maskCDMipId) {
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
                ),
                /*from*/0,
                /*nRecords*/-1,
                /*readPageSize*/0);
        // select best matches to process
        List<CDMatchEntity<M, T>> bestMatches = ColorMIPProcessUtils.selectBestMatches(
                allCDMatches,
                args.numberOfBestLines,
                args.numberOfBestSamplesPerLine,
                args.numberOfBestMatchesPerSample
        );
        if (LOG.isDebugEnabled()) {
            // log best lines
            String maskName = bestMatches.stream().findFirst().map(m -> m.getMaskImage().getPublishedName()).orElse("mask not found");
            List<String> targetNames = bestMatches.stream()
                    .map(m -> m.getMatchedImage().getPublishedName() + "/" + m.getMatchedMIPId())
                    .distinct()
                    .collect(Collectors.toList());
            LOG.debug("Selected a total of {} best matches for {} target names for {} matches: {}", bestMatches.size(), targetNames.size(), maskName, targetNames);
        }
        LOG.info("Selected {} best color depth matches for {} out of {} total matches: {}",
                bestMatches.size(), maskCDMipId, allCDMatches.size(), getShortenedName(bestMatches, 10, m -> m.getEntityId().toString()));
        return MatchEntitiesGrouping.groupMatchesByMaskID(bestMatches);
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
            DaosProvider daosProvider = getDaosProvider(false);
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
            return new DBNeuronMatchesWriter<>(getDaosProvider(false).getCDMatchesDao());
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
            return new DBCheckedCDMIPsWriter(getDaosProvider(false).getNeuronMetadataDao());
        } else {
            return null;
        }
    }

    /**
     * Create a stream of gradscore computations for the specified mask and its matches. The purpose of this is to load the mask and create the algorithm for the mask only once.
     *
     * @param mask
     * @param maskMatches
     * @param shapeScoreAlgorithmProvider
     * @param <M>
     * @param <T>
     * @return
     */
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    Flux<Pair<ColorDepthSearchAlgorithm<ShapeMatchScore>, CDMatchEntity<M, T>>> createGradScoreComputationsForMask(M mask,
                                                                                                                   List<CDMatchEntity<M, T>> maskMatches,
                                                                                                                   ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider) {
        if (maskMatches.isEmpty()) {
            return Flux.empty(); // nothing to do
        }
        LOG.info("Load mask image {}", mask);
        NeuronMIP<M> maskImage = NeuronMIPUtils.loadComputeFile(mask, ComputeFileType.InputColorDepthImage);
        if (NeuronMIPUtils.hasNoImageArray(maskImage)) {
            LOG.error("No image found for {}", mask);
            return Flux.empty(); // nothing can be done because mask image is missing
        }
        ColorDepthSearchAlgorithm<ShapeMatchScore> shapeScoreAlgorithm =
                shapeScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        maskImage.getImageArray(),
                        args.maskThreshold,
                        args.borderSize);
        // use Flux.generate instead of Flux.fromIterable because then I don't have to worry about the backpressure
        // the method will be called only one data is needed by the downstream
        return Flux.generate(
                () -> new GeneratorState<>(maskMatches),
                (state, sink) -> {
                    if (!state.hasNext()) {
                        sink.complete();
                        checkMemoryUsage();
                        return state;
                    }
                    CDMatchEntity<M, T> cdsMatch = state.next();
                    if (cdsMatch == null) {
                        sink.complete();
                        checkMemoryUsage();
                        return state;
                    }
                    sink.next(Pair.of(shapeScoreAlgorithm, cdsMatch));
                    return state;
                }
        );
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    Flux<CDMatchEntity<M, T>> runGradientScoreComputations(List<Pair<ColorDepthSearchAlgorithm<ShapeMatchScore>, CDMatchEntity<M, T>>> algsPlusMatches) {
        // use Flux.generate instead of Flux.fromIterable because then I don't have to worry about the backpressure
        // the method will be called only one data is needed by the downstream
        return Flux.generate(
                () -> new GeneratorState<>(algsPlusMatches),
                (state, sink) -> {
                    if (!state.hasNext()) {
                        sink.complete();
                        checkMemoryUsage();
                        return state;
                    }
                    Pair<ColorDepthSearchAlgorithm<ShapeMatchScore>, CDMatchEntity<M, T>> algPlusMatch = state.next();
                    if (algPlusMatch == null) {
                        sink.complete();
                        checkMemoryUsage();
                        return state;
                    }
                    ColorDepthSearchAlgorithm<ShapeMatchScore> shapeScoreAlgorithm = algPlusMatch.getLeft();
                    CDMatchEntity<M, T> cdsMatch = algPlusMatch.getRight();
                    long startCalcTime = System.currentTimeMillis();
                    M mask = cdsMatch.getMaskImage();
                    T target = cdsMatch.getMatchedImage();
                    MDC.put("maskId", mask.getMipId() + "/" + mask.getEntityId());
                    MDC.put("targetId", target.getMipId() + "/" + target.getEntityId());
                    try {
                        NeuronMIP<AbstractNeuronEntity> matchedTargetImage = CachedMIPsUtils.loadMIP(target, ComputeFileType.InputColorDepthImage);
                        if (NeuronMIPUtils.hasImageArray(matchedTargetImage)) {
                            LOG.debug("Calculate shape score for {} between {}:{} and {}:{}",
                                    cdsMatch.getEntityId(),
                                    mask.getPublishedName(), mask.getMipId(),
                                    target.getPublishedName(), target.getMipId());
                            ShapeMatchScore gradScore = shapeScoreAlgorithm.calculateMatchingScore(
                                    matchedTargetImage.getImageArray(),
                                    NeuronMIPUtils.getImageLoaders(
                                            target,
                                            shapeScoreAlgorithm.getRequiredTargetVariantTypes(),
                                            (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadMIP(n, cft))
                                    )
                            );
                            // only set the scores that were actually calculated
                            if (gradScore.getGradientAreaGap() != -1) {
                                cdsMatch.setGradientAreaGap(gradScore.getGradientAreaGap() );
                            }
                            if (gradScore.getHighExpressionArea() != -1) {
                                cdsMatch.setHighExpressionArea(gradScore.getHighExpressionArea());
                            }
                            if (gradScore.getBidirectionalAreaGap() != -1) {
                                cdsMatch.setBidirectionalAreaGap(gradScore.getBidirectionalAreaGap());
                            }
                            LOG.debug("Shape score for {} between {}:{} and {}:{} is {}/{}/{} -> {} - computed in {}ms",
                                    cdsMatch.getEntityId(),
                                    mask.getPublishedName(), mask.getMipId(),
                                    target.getPublishedName(), target.getMipId(),
                                    gradScore.getGradientAreaGap(), gradScore.getHighExpressionArea(), gradScore.getBidirectionalAreaGap(), gradScore,
                                    System.currentTimeMillis() - startCalcTime);
                        } else {
                            LOG.warn("No image found for {}", target);
                            cdsMatch.setBidirectionalAreaGap(-1L);
                            cdsMatch.setGradientAreaGap(-1L);
                            cdsMatch.setHighExpressionArea(-1L);
                        }
                        sink.next(cdsMatch);
                        return state;
                    } finally {
                        MDC.remove("maskId");
                        MDC.remove("targetId");
                    }
                }
        );
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
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

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void normalizeScores(List<CDMatchEntity<M, T>> cdMatches) {
        LOG.info("Normalize gradient scores for {} matches: {}", cdMatches.size(), getShortenedName(cdMatches, 20, m -> m.getEntityId().toString()));
        // group matches by mask to get max scores for normalization
        List<GroupedItems<M, CDMatchEntity<M, T>>> cdMatchesGroupedByMask = MatchEntitiesGrouping.groupMatchesByMaskID(cdMatches);
        cdMatchesGroupedByMask.parallelStream().forEach(matchesByMask -> {
            // get max scores for normalization
            CombinedMatchScore maxScores = matchesByMask.getItems().stream()
                    .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                    .reduce(new CombinedMatchScore(-1, -1L),
                            (s1, s2) -> new CombinedMatchScore(
                                    Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                    Math.max(s1.getGradScore(), s2.getGradScore())));
            LOG.info("Max scores for {} matches is {}", matchesByMask.getKey(), maxScores);
            // update normalized scores for all matches for this mask
            matchesByMask.getItems().forEach(m -> {
                double normalizedScore = GradientAreaGapUtils.calculateNormalizedScore(
                        m.getMatchingPixels(),
                        m.getGradScore(),
                        maxScores.getPixelMatches(),
                        maxScores.getGradScore()
                );
                LOG.debug("Set normalized score for match {} ({}:{} vs {}:{}) to {}",
                        m.getEntityId(),
                        m.getMaskImage().getPublishedName(), m.getMaskImage().getMipId(),
                        m.getMatchedImage().getPublishedName(), m.getMatchedImage().getMipId(),
                        normalizedScore);
                m.updateNormalizedScore((float) normalizedScore);
            });
        });
    }

}
