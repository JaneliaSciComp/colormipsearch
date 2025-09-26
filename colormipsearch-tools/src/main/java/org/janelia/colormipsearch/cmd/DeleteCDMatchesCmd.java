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

import com.beust.jcommander.Parameter;
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
import org.janelia.colormipsearch.dataio.NeuronMatchesRemover;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesRemover;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
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
class DeleteCDMatchesCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteCDMatchesCmd.class);

    @Parameters(commandDescription = "Delete matches based on specified filters")
    static class DeleteMatchesArgs extends AbstractGradientScoresArgs {
        DeleteMatchesArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        @Parameter(names = {"--no-archive"},
                description = "Do not archive matches when deleting them (only applies to DB storage)",
                arity = 0)
        boolean noArchiveOnDelete = false;
    }

    private final DeleteMatchesArgs args;
    private final ObjectMapper mapper;

    DeleteCDMatchesCmd(String commandName,
                       CommonArgs commonArgs) {
        super(commandName);
        this.args = new DeleteMatchesArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    DeleteMatchesArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // run gradient scoring
        deleteAllCDMatches();
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void deleteAllCDMatches() {
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
            List<CDMatchEntity<M, T>> deletedMatches = Flux.fromIterable(maskIdsToProcess)
                    .buffer(bufferingSize)
                    .parallel(CmdUtils.getTaskConcurrency(args.commonArgs))
                    .runOn(scheduler)
                    .map(maskIds -> {
                        LOG.info("Retrieve matches for {} masks", maskIds.size());
                        return getCDMatchesForMasks(cdMatchesReader, maskIds);
                    })
                    .flatMap(this::deleteCDMatches)
                    .sequential()
                    .collectList()
                    .block();
            System.gc(); // force garbage collection
            LOG.info("Finished deleting {} items in {}s - memory usage {}M out of {}M",
                    deletedMatches.size(),
                    (System.currentTimeMillis() - startTime) / 1000.,
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

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesRemover<CDMatchEntity<M, T>> getCDMatchesRemover() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesRemover<>(getDaosProvider().getCDMatchesDao(), !args.noArchiveOnDelete);
        } else {
            return matches -> 0; // no-op for file system storage
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> Flux<CDMatchEntity<M, T>> deleteCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        NeuronMatchesRemover<CDMatchEntity<M, T>> matchesRemover = getCDMatchesRemover();
        LOG.info("Delete {} matches", cdMatches.size());
        long ndeleted = matchesRemover.delete(cdMatches);
        LOG.info("Deleted {} matches", ndeleted);
        return Flux.fromIterable(cdMatches);
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
