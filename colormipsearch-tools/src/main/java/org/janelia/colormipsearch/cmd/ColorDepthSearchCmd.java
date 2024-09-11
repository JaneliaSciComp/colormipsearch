package org.janelia.colormipsearch.cmd;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProviderFactory;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cds.PixelMatchScore;
import org.janelia.colormipsearch.cmd.cdsprocess.ColorMIPSearchProcessor;
import org.janelia.colormipsearch.cmd.cdsprocess.LocalColorMIPSearchProcessor;
import org.janelia.colormipsearch.cmd.cdsprocess.SparkColorMIPSearchProcessor;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.CDMIPsReader;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.CDSSessionWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCDMIPsReader;
import org.janelia.colormipsearch.dataio.db.DBCDSSessionWriter;
import org.janelia.colormipsearch.dataio.db.DBCDScoresOnlyWriter;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONCDMIPsReader;
import org.janelia.colormipsearch.dataio.fs.JSONCDSSessionWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command that runs the Color Depth Search.
 */
class ColorDepthSearchCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ColorDepthSearchCmd.class);

    @Parameters(commandDescription = "Color depth search for a batch of MIPs")
    static class ColorDepthSearchArgs extends AbstractColorDepthMatchArgs {

        @Parameter(names = {"--mips-storage"},
                description = "Specifies MIPs storage")
        StorageType mipsStorage = StorageType.DB;

        @Parameter(names = {"--update-matches"},
                description = "If set a new color depth search run will always create new results; " +
                        "the default behavior is to update entries that match same images", arity = 0)
        boolean updateExistingMatches = false;

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space: {JRC2018_Unisex_20x_HR, JRC2018_VNC_Unisex_40x_DS} ", required = true)
        String alignmentSpace;

        @Parameter(names = {"--masks", "-m"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Image file(s) to use as the search masks")
        List<ListArg> masksLibraries;

        @Parameter(names = {"--masks-index"}, description = "Mask file(s) start index")
        long masksStartIndex;

        @Parameter(names = {"--masks-length"}, description = "Mask file(s) length")
        int masksLength;

        @Parameter(names = {"--targets", "-i"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Comma-delimited list of JSON configs containing images to search")
        List<ListArg> targetsLibraries;

        @Parameter(names = {"--targets-index"}, description = "Input image file(s) start index")
        long targetsStartIndex;

        @Parameter(names = {"--targets-length"}, description = "Input image file(s) length")
        int targetsLength;

        @Parameter(names = {"--masks-tags"}, description = "Masks MIPs tags to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksTags;

        @Parameter(names = {"--masks-terms"}, description = "Masks MIPs annotations (terms) to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksAnnotations;

        @Parameter(names = {"--excluded-masks-terms"}, description = "Masks MIPs annotations (terms) to be excluded for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> excludedMasksAnnotations;

        @Parameter(names = {"--masks-datasets"}, description = "Masks MIPs datasets to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksDatasets;

        @Parameter(names = {"--targets-tags"}, description = "Targets MIPs tags to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsTags;

        @Parameter(names = {"--targets-terms"}, description = "Targets MIPs annotations (terms) to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsAnnotations;

        @Parameter(names = {"--excluded-targets-terms"}, description = "Targets MIPs annotations (terms) to be excluded for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> excludedTargetsAnnotations;

        @Parameter(names = {"--targets-datasets"}, description = "Targets MIPs datasets to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsDatasets;

        @Parameter(names = {"--masks-published-names"}, description = "Masks MIPs published names to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksPublishedNames;

        @Parameter(names = {"--targets-published-names"}, description = "Targets MIPs published names to be selected for CDS",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsPublishedNames;

        @Parameter(names = {"--processing-tag"}, required = true,
                description = "Associate this tag with the run. Also all MIPs that are color depth searched will be stamped with this processing tag")
        String processingTag;

        @Parameter(names = {"--write-batch-size"}, description = "If this is set the results will be written in batches of this size")
        int writeBatchSize = 0;

        @Parameter(names = {"--parallel-write-results"},
                description = "If set, result batches are written concurrently. This option is used only when the results destination is the database",
                arity = 0)
        boolean parallelWriteResults = false;

        @Parameter(names = {"--use-spark"}, arity = 0,
                   description = "If set, use spark to run color depth search process")
        boolean useSpark;

        ColorDepthSearchArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        String getProcessingTag() {
            return processingTag.trim();
        }
    }

    private final ColorDepthSearchArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final ObjectMapper mapper;

    ColorDepthSearchCmd(String commandName,
                        CommonArgs commonArgs,
                        Supplier<Long> cacheSizeSupplier) {
        super(commandName);
        this.args = new ColorDepthSearchArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    ColorDepthSearchArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // initialize the cache
        CachedMIPsUtils.initializeCache(cacheSizeSupplier.get());
        // perform color depth search for all masks against all targets
        runColorDepthSearch();
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, P extends RGBPixelType<P>, G> void runColorDepthSearch() {
        CDMIPsReader cdmipsReader = getCDMipsReader();
        ColorMIPSearchProcessor<M, T> colorMIPSearchProcessor;
        ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider;
        cdsAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createPixMatchCDSAlgorithmProvider(
                args.mirrorMask,
                args.dataThreshold,
                args.pixColorFluctuation,
                args.xyShift,
                args.getColorScaleAndLabelRegionCondition()
        );
        ColorMIPSearch colorMIPSearch = new ColorMIPSearch(
                args.pctPositivePixels,
                args.maskThreshold,
                cdsAlgorithmProvider);
        @SuppressWarnings("unchecked")
        List<M> maskMips = (List<M>) readMIPs(cdmipsReader,
                args.masksLibraries,
                args.masksPublishedNames,
                args.masksDatasets,
                args.masksTags,
                args.masksAnnotations,
                args.excludedMasksAnnotations,
                args.masksStartIndex, args.masksLength,
                args.maskMIPsFilter);
        LOG.info("Read {} masks", maskMips.size());
        @SuppressWarnings("unchecked")
        List<T> targetMips = (List<T>) readMIPs(cdmipsReader,
                args.targetsLibraries,
                args.targetsPublishedNames,
                args.targetsDatasets,
                args.targetsTags,
                args.targetsAnnotations,
                args.excludedTargetsAnnotations,
                args.targetsStartIndex, args.targetsLength,
                args.libraryMIPsFilter);
        LOG.info("Read {} targets", targetMips.size());
        if (maskMips.isEmpty() || targetMips.isEmpty()) {
            LOG.info("Nothing to do for {} masks and {} targets", maskMips.size(), targetMips.size());
            return;
        }
        Set<String> processingTags = Collections.singleton(args.getProcessingTag());
        // save CDS parameters
        Number cdsRunId = getCDSSessionWriter().createSession(
                args.masksLibraries.stream()
                        .map(larg -> new DataSourceParam()
                                .setAlignmentSpace(args.alignmentSpace)
                                .addLibrary(larg.input)
                                .addNames(args.masksPublishedNames)
                                .addDatasets(args.masksDatasets)
                                .addTags(args.masksTags)
                                .setOffset(larg.offset)
                                .setSize(larg.length))
                        .collect(Collectors.toList()),
                args.targetsLibraries.stream()
                        .map(larg -> new DataSourceParam()
                                .setAlignmentSpace(args.alignmentSpace)
                                .addLibrary(larg.input)
                                .addNames(args.targetsPublishedNames)
                                .addDatasets(args.targetsDatasets)
                                .addTags(args.targetsTags)
                                .setOffset(larg.offset)
                                .setSize(larg.length))
                        .collect(Collectors.toList()),
                colorMIPSearch.getCDSParameters(),
                processingTags);
        LOG.info("Created CDS session {} for processing tags {}", cdsRunId, processingTags);
        if (args.useSpark) {
            colorMIPSearchProcessor = new SparkColorMIPSearchProcessor<>(
                    cdsRunId,
                    args.appName,
                    colorMIPSearch,
                    args.processingPartitionSize,
                    processingTags
            );
        } else {
            colorMIPSearchProcessor = new LocalColorMIPSearchProcessor<>(
                    cdsRunId,
                    colorMIPSearch,
                    args.processingPartitionSize,
                    CmdUtils.createCmdExecutor(args.commonArgs),
                    processingTags
            );
        }
        List<CDMatchEntity<M, T>> cdsResults;
        try {
            // start the pairwise color depth search
             cdsResults = colorMIPSearchProcessor.findAllColorDepthMatches(maskMips, targetMips);
        } catch (Exception e) {
            LOG.error("Error while finding color depth matches", e);
            throw new IllegalStateException(e);
        }
        try {
            if (cdsResults.isEmpty()) {
                LOG.info("No matches found!!!");
            } else {
                // clean up the cache as the mips are no longer needed at this point
                CachedMIPsUtils.cleanCache();
                // Then force a gc
                System.gc();
                LOG.info("Start writing {} color depth search results - memory usage {}M out of {}M",
                        cdsResults.size(),
                        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                        (Runtime.getRuntime().totalMemory() / _1M));
                if (args.writeBatchSize > 0) {
                    Stream<Map.Entry<Integer, List<CDMatchEntity<M, T>>>> cdsResultsPartitionedStream;
                    if (args.parallelWriteResults && args.commonArgs.resultsStorage == StorageType.DB) {
                        cdsResultsPartitionedStream = ItemsHandling.partitionCollection(cdsResults, args.writeBatchSize)
                                .entrySet()
                                .parallelStream();
                    } else {
                        cdsResultsPartitionedStream = ItemsHandling.partitionCollection(cdsResults, args.writeBatchSize)
                                .entrySet()
                                .stream();
                    }
                    cdsResultsPartitionedStream
                            .forEach(e -> {
                                Integer i = e.getKey();
                                List<CDMatchEntity<M, T>> resultsBatch = e.getValue();
                                saveCDSResults(i, resultsBatch);
                            });
                } else {
                    saveCDSResults(0, cdsResults);
                }
                LOG.info("Finished writing {} color depth search results  - memory usage {}M out of {}M",
                        cdsResults.size(),
                        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                        (Runtime.getRuntime().totalMemory() / _1M));
            }
        } catch (Exception e) {
            LOG.error("Error writing color depth match results", e);
        } finally {
            LOG.info("Set processing tags to {}:{}", ProcessingType.ColorDepthSearch, processingTags);
            // update the mips processing tags
            getCDMipsWriter().ifPresent(cdmipsWriter -> {
                cdmipsWriter.addProcessingTags(
                        filterProcessedNeurons(maskMips, processingTags),
                        ProcessingType.ColorDepthSearch,
                        processingTags);
                cdmipsWriter.addProcessingTags(
                        filterProcessedNeurons(targetMips, processingTags),
                        ProcessingType.ColorDepthSearch,
                        processingTags);
            });
            LOG.info("Finished setting processing tags to {}:{}", ProcessingType.ColorDepthSearch, processingTags);
            colorMIPSearchProcessor.terminate();
        }
    }

    private CDMIPsReader getCDMipsReader() {
        if (args.mipsStorage == StorageType.DB) {
            return new DBCDMIPsReader(getDaosProvider().getNeuronMetadataDao());
        } else {
            return new JSONCDMIPsReader(mapper);
        }
    }

    private Optional<CDMIPsWriter> getCDMipsWriter() {
        if (args.mipsStorage == StorageType.DB) {
            return Optional.of(new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao()));
        } else {
            return Optional.empty();
        }
    }

    private CDSSessionWriter getCDSSessionWriter() {
        if (args.mipsStorage == StorageType.DB) {
            return new DBCDSSessionWriter(getDaosProvider().getMatchParametersDao());
        } else {
            return new JSONCDSSessionWriter(
                    args.getOutputDir(),
                    mapper);
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    NeuronMatchesWriter<CDMatchEntity<M, T>> getCDSMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            DaosProvider daosProvider = getDaosProvider();
            if (args.updateExistingMatches) {
                // if a match exists update the scores
                return new DBCDScoresOnlyWriter<>(daosProvider.getCDMatchesDao());
            } else {
                // always create new matches
                return new DBNeuronMatchesWriter<>(daosProvider.getCDMatchesDao());
            }
        } else {
            return new JSONNeuronMatchesWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    AbstractNeuronEntity::getMipId, // group results by neuron MIP ID
                    Comparator.comparingDouble(m -> -(((CDMatchEntity<?,?>) m).getMatchingPixels())), // descending order by matching pixels
                    args.getPerMaskDir(),
                    args.getPerTargetDir()
            );
        }
    }

    private List<? extends AbstractNeuronEntity> readMIPs(CDMIPsReader mipsReader,
                                                          List<ListArg> mipsLibraries,
                                                          List<String> mipsPublishedNames,
                                                          List<String> mipsDatasets,
                                                          List<String> mipsTags,
                                                          List<String> mipsAnnotations,
                                                          List<String> excludedMipsAnnotations,
                                                          long startIndexArg, int length,
                                                          Set<String> filter) {
        long startIndex = startIndexArg > 0 ? startIndexArg : 0;
        List<? extends AbstractNeuronEntity> allMips = mipsLibraries.stream()
                .flatMap(libraryInput -> mipsReader.readMIPs(
                        new DataSourceParam()
                                .setAlignmentSpace(args.alignmentSpace)
                                .addLibrary(libraryInput.input)
                                .addNames(mipsPublishedNames)
                                .addDatasets(mipsDatasets)
                                .addTags(mipsTags)
                                .addAnnotations(mipsAnnotations)
                                .addExcludedAnnotations(excludedMipsAnnotations)
                                .setOffset(libraryInput.offset)
                                .setSize(libraryInput.length)).stream())
                .filter(neuronMetadata -> CollectionUtils.isEmpty(filter) ||
                        filter.contains(neuronMetadata.getPublishedName().toLowerCase()) ||
                        filter.contains(neuronMetadata.getMipId()))
                .skip(startIndex)
                .collect(Collectors.toList());
        return length > 0 && length < allMips.size()
                ? allMips.subList(0, length)
                : allMips;
    }

    private <N extends AbstractNeuronEntity> List<N> filterProcessedNeurons(List<N> neurons, Set<String> processedTags) {
        return neurons.stream().filter(n -> n.hasProcessedTags(ProcessingType.ColorDepthSearch, processedTags)).collect(Collectors.toList());
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long saveCDSResults(int batchId, List<CDMatchEntity<M, T>> cdsBatchResults) {
        LOG.info("Results batch: {} - write {} matches - memory usage {}M out of {}M",
                batchId, cdsBatchResults.size(),
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        NeuronMatchesWriter<CDMatchEntity<M, T>> cdsResultsWriter = getCDSMatchesWriter();
        long n = cdsResultsWriter.write(cdsBatchResults);
        LOG.info("Finished batch: {} - {} matches - memory usage {}M out of {}M",
                batchId, n,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        System.gc(); // force garbage collection after each batch
        return n;
    }
}
