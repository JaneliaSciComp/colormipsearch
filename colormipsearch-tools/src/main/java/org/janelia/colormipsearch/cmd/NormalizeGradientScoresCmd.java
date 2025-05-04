package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.cds.CombinedMatchScore;
import org.janelia.colormipsearch.cds.GradientAreaGapUtils;
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
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Command to calculate the gradient scores.
 */
class NormalizeGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(NormalizeGradientScoresCmd.class);

    @Parameters(commandDescription = "Normalize gradient scores. The scores will be normalized with respect to the selected subset based on specified target filters")
    static class NormalizeGradientScoresArgs extends AbstractGradientScoresArgs {
        NormalizeGradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final NormalizeGradientScoresArgs args;
    private final ObjectMapper mapper;

    NormalizeGradientScoresCmd(String commandName,
                               CommonArgs commonArgs) {
        super(commandName);
        this.args = new NormalizeGradientScoresArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    NormalizeGradientScoresArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // run gradient scoring
        normalizeAllGradientScores();
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void normalizeAllGradientScores() {
        long startTime = System.currentTimeMillis();
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
        if (CollectionUtils.isEmpty(maskIdsToProcess)) {
            LOG.info("No masks were selected");
            return; // nothing to do
        }
        int bufferingSize = args.processingPartitionSize > 0
                ? args.processingPartitionSize
                : 1;
        LOG.info("Split work into {} partitions of size {} - memory usage {}M out of {}M",
                maskIdsToProcess.size() / bufferingSize + 1, bufferingSize,
                (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (maxMemory / _1M));
        ExecutorService executorService = CmdUtils.createCmdExecutor(args.commonArgs);
        try {
            Scheduler scheduler = Schedulers.fromExecutorService(executorService);
            List<CDMatchEntity<M, T>> normalizedMatches = Flux.fromIterable(maskIdsToProcess)
                    .buffer(bufferingSize)
                    .parallel(CmdUtils.getTaskConcurrency(args.commonArgs))
                    .runOn(scheduler)
                    .map(maskIds -> {
                        LOG.info("Retrieve matches for {} masks", maskIds.size());
                        return getCDMatchesForMasks(cdMatchesReader, maskIds);
                    })
                    .map(matches -> MatchEntitiesGrouping.simpleGroupByMaskFields(
                            matches,
                            Arrays.asList(
                                    AbstractNeuronEntity::getMipId,
                                    m -> m.getComputeFileName(ComputeFileType.InputColorDepthImage)
                            )
                    ))
                    .flatMap(groupedMatches -> updateNormalizedScoresBatch(groupedMatches))
                    .sequential()
                    .collectList()
                    .block();
            LOG.info("Finished normalizing scores for {} items in {}s - memory usage {}M out of {}M",
                    normalizedMatches.size(),
                    (System.currentTimeMillis() - startTime) / 1000.,
                    (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (maxMemory / _1M));
            long startUpdateTime = System.currentTimeMillis();
            long updated = updateCDMatches(normalizedMatches);
            LOG.info("Finished updating scores for {} items ({} updated) in {}s - memory usage {}M out of {}M",
                    normalizedMatches.size(), updated,
                    (System.currentTimeMillis() - startUpdateTime) / 1000.,
                    (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (maxMemory / _1M));
            long updatesWithProcessedTag = updateProcessingTag(normalizedMatches);
            LOG.info("Annotated {} with {} in {}s - memory usage {}M out of {}M",
                    updatesWithProcessedTag,
                    ProcessingType.NormalizeGradientScore,
                    (System.currentTimeMillis() - startUpdateTime) / 1000.,
                    (maxMemory - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                    (maxMemory / _1M));
        } finally {
            executorService.shutdown();
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

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> Flux<CDMatchEntity<M, T>> updateNormalizedScoresBatch(List<GroupedMatchedEntities<M, T, CDMatchEntity<M, T>>> groupedCDMatchesBatch) {
        return Flux.fromIterable(groupedCDMatchesBatch)
                .doOnNext(groupedCDMatches -> {
                    long startProcessingPartitionTime = System.currentTimeMillis();
                    String maskId = groupedCDMatches.getKey().getMipId();
                    List<CDMatchEntity<M, T>> cdMatches = groupedCDMatches.getItems();
                    LOG.info("Processing {} matches for {}", cdMatches.size(), maskId);
                    // normalize the grad scores
                    updateNormalizedScores(cdMatches);
                    LOG.info("Finished normalizing {} scores for {} matches in {}s- memory usage {}M out of {}M",
                            cdMatches.size(),
                            maskId,
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                })
                .flatMap(groupedCDMatches -> Flux.fromIterable(groupedCDMatches.getItems()))
                ;
    }

    /**
     * The method calculates and updates normalized gradient scores for all color depth matches of the given mask MIP ID.
     *
     * @param cdMatches color depth matches for which the grad score will be computed
     * @param <M>       mask type
     * @param <T>       target type
     */
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void updateNormalizedScores(List<CDMatchEntity<M, T>> cdMatches) {
        Set<String> masksNames = cdMatches.stream()
                .map(cdm -> cdm.getMaskImage().getPublishedName())
                .collect(Collectors.toSet());
        // get max scores for normalization
        CombinedMatchScore maxScores = cdMatches.stream()
                .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                .reduce(new CombinedMatchScore(-1, -1L),
                        (s1, s2) -> new CombinedMatchScore(
                                Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                Math.max(s1.getGradScore(), s2.getGradScore())));
        LOG.info("Max scores for {} matches is {}", masksNames, maxScores);
        // update normalized score
        cdMatches.forEach(m -> m.setNormalizedScore((float) GradientAreaGapUtils.calculateNormalizedScore(
                m.getMatchingPixels(),
                m.getGradScore(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        NeuronMatchesWriter<CDMatchEntity<M, T>> matchesWriter = getCDMatchesWriter();
        return matchesWriter.writeUpdates(
                cdMatches,
                Arrays.asList(
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore()), // only update the normalized score
                        m -> ImmutablePair.of("updatedDate", new Date())
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateProcessingTag(List<CDMatchEntity<M, T>> cdMatches) {
        Set<String> processingTags = Collections.singleton(args.getProcessingTag());
        CDMIPsWriter cdmipsWriter = getCDMipsWriter();
        if (cdmipsWriter == null) {
            return 0;
        }
        Set<AbstractNeuronEntity> mipsToUpdate = cdMatches.stream()
                .flatMap(m -> Stream.of(m.getMaskImage(), m.getMatchedImage()))
                .collect(Collectors.toSet());
        return cdmipsWriter.addProcessingTags(mipsToUpdate, ProcessingType.NormalizeGradientScore, processingTags);
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMasks(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, Collection<String> maskCDMipIds) {
        LOG.info("Start reading all color depth matches for {} mips", maskCDMipIds.size());
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (args.pctPositivePixels > 0) {
            neuronsMatchScoresFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        neuronsMatchScoresFilter.addSScore("gradientAreaGap", 0);
        // return all matches for this mipID that have a gradient score
        // the "targets" filtering will be used for normalizing the score for the selected targets
        return cdsMatchesReader.readMatchesByMask(
                args.alignmentSpace,
                new DataSourceParam()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addMipIDs(maskCDMipIds)
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
                /*matchTags*/args.matchTags,
                /*matchExcludedTags*/null,
                neuronsMatchScoresFilter,
                /*sortCriteria*/Collections.emptyList(),
                /*readPageSize*/0);
    }
}
