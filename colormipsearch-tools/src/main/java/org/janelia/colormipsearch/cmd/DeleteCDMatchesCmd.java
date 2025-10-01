package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesRemover;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesRemover;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesReader;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Command to calculate the gradient scores.
 */
class DeleteCDMatchesCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteCDMatchesCmd.class);

    @Parameters(commandDescription = "Delete matches based on specified filters")
    static class DeleteMatchesArgs extends AbstractCmdArgs {
        DeleteMatchesArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space: {JRC2018_Unisex_20x_HR, JRC2018_VNC_Unisex_40x_DS} ", required = true)
        String alignmentSpace;

        @Parameter(names = {"--masks-libraries", "-md"}, required = true, variableArity = true,
                converter = ListArg.ListArgConverter.class,
                description = "Masks libraries; for JSON results this is interpreted as the location of the match files")
        List<ListArg> masksLibraries;

        @Parameter(names = {"--masks-published-names"}, description = "Masks published names to be selected for gradient scoring",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksPublishedNames = new ArrayList<>();

        @Parameter(names = {"--masks-mips"}, description = "Selected mask MIPs",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksMIPIDs;

        @Parameter(names = {"--masks-datasets"}, description = "Datasets associated with the mask of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> maskDatasets = new ArrayList<>();

        @Parameter(names = {"--masks-tags"}, description = "Tags associated with the mask of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> maskTags = new ArrayList<>();

        @Parameter(names = {"--masks-terms"}, description = "Terms associated with the mask of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> maskAnnotations = new ArrayList<>();

        @Parameter(names = {"--excluded-masks-terms"}, description = "Terms associated with the mask of the match to NOT be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> excludedMaskAnnotations = new ArrayList<>();

        @Parameter(names = {"--masks-processing-tags"}, description = "Masks processing tags",
                converter = NameValueArg.NameArgConverter.class)
        List<NameValueArg> maskProcessingTags = new ArrayList<>();

        @Parameter(names = {"--targets-datasets"}, description = "Datasets associated with the target of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetDatasets = new ArrayList<>();

        @Parameter(names = {"--targets-tags"}, description = "Tags associated with the target of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetTags = new ArrayList<>();

        @Parameter(names = {"--targets-libraries"}, description = "Target libraries for the selected matches",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsLibraries;

        @Parameter(names = {"--targets-published-names"}, description = "Selected target names",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsPublishedNames;

        @Parameter(names = {"--targets-mips"}, description = "Selected target MIPs",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsMIPIDs;

        @Parameter(names = {"--targets-terms"}, description = "Terms associated with the target of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetAnnotations = new ArrayList<>();

        @Parameter(names = {"--excluded-targets-terms"}, description = "Terms associated with the target of the match to NOT be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> excludedTargetAnnotations = new ArrayList<>();

        @Parameter(names = {"--targets-processing-tags"}, description = "Targets processing tags",
                converter = NameValueArg.NameArgConverter.class)
        List<NameValueArg> targetsProcessingTags = new ArrayList<>();

        @Parameter(names = {"--no-archive"},
                description = "Do not archive matches when deleting them (only applies to DB storage)",
                arity = 0)
        boolean noArchiveOnDelete = false;

        @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
        int processingPartitionSize = 100;

        @Parameter(names = {"--delete-batch-size"}, description = "Delete batch size")
        int deleteBatchSize = -1;

        @Parameter(names = {"--fetch-page-size"}, description = "Page size used to fetch matches to be deleted")
        int fetchPageSize = 1000;

        @Parameter(names = {"--match-tags"}, description = "Match tags to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> matchTags = new ArrayList<>();

        @Parameter(names = {"--include-matches-with-gradscore"}, arity = 0, description = "Delete matches that have a grad score too")
        boolean includeMatchesWithShapeScore = false;

        Map<String, Collection<String>> getMasksProcessingTags() {
            return maskProcessingTags.stream().collect(Collectors.toMap(
                    NameValueArg::getArgName,
                    nv -> new HashSet<>(nv.getArgValues())));
        }

        Map<String, Collection<String>> getTargetsProcessingTags() {
            return targetsProcessingTags.stream().collect(Collectors.toMap(
                    NameValueArg::getArgName,
                    nv -> new HashSet<>(nv.getArgValues())));
        }

        int getProcessingPartitionSize() {
            return processingPartitionSize > 0 ? processingPartitionSize : 1;
        }

    }

    static class MaskIDsDeleteState {
        final AtomicLong offset;
        final List<String> maskIds;
        MaskIDsDeleteState(List<String> maskIds) {
            this.offset = new AtomicLong(0L);
            this.maskIds = maskIds;
        }
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
                    .flatMap(currentMaskIds -> {
                        LOG.info("Retrieve matches for {} masks", currentMaskIds.size());
                        return Flux.generate(
                                () -> new MaskIDsDeleteState(currentMaskIds),
                                (MaskIDsDeleteState state, SynchronousSink<List<CDMatchEntity<M, T>>> sink) -> {
                                    LOG.info("Retrieve {} matches for {} starting at offset {}",
                                            bufferingSize,
                                            getShortenedName(state.maskIds, 5, Function.identity()),
                                            state.offset);
                                    List<CDMatchEntity<M, T>> maskMatches = getCDMatchesForMasks(cdMatchesReader, state.maskIds, state.offset.get(), args.deleteBatchSize, args.fetchPageSize);
                                    if (CollectionUtils.isEmpty(maskMatches)) {
                                        LOG.info("No more matches found for masks {}",
                                                getShortenedName(state.maskIds, 5, Function.identity()));
                                        sink.complete();
                                    }
                                    state.offset.addAndGet(maskMatches.size());
                                    sink.next(maskMatches);
                                    return state;
                                }
                        );
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
            LOG.info("Delete operation is only implemented for DB storage - no-op for file system storage");
            return matches -> 0; // no-op for file system storage
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> Flux<CDMatchEntity<M, T>> deleteCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        NeuronMatchesRemover<CDMatchEntity<M, T>> matchesRemover = getCDMatchesRemover();
        String truncatedDeletes = getShortenedName(cdMatches, 20, m -> m.getEntityId().toString());
        LOG.info("Delete {} matches: {}", cdMatches.size(), truncatedDeletes);
        long ndeleted = matchesRemover.delete(cdMatches);
        LOG.info("Deleted {} matches: {}", ndeleted, truncatedDeletes);
        return Flux.fromIterable(cdMatches);
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMasks(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, Collection<String> maskCDMipIds,
                                                   long from, int n, int pageSize) {
        LOG.info("Start reading all color depth matches for {} mips", maskCDMipIds.size());
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (!args.includeMatchesWithShapeScore) {
            // by default we only delete matches that do not have a gradient score
            neuronsMatchScoresFilter.addSScore("gradientAreaGap|bidirectionalAreaGap", -1);
        }
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
                /*from*/from,
                /*nRecords*/n,
                /*readPageSize*/pageSize);
    }

    private <T> String getShortenedName(List<T> elems, int maxLen, Function<T, String> toStrFunc) {
        return elems.stream().map(toStrFunc).limit(maxLen).collect(Collectors.joining(",", "", "..."));
    }
}
