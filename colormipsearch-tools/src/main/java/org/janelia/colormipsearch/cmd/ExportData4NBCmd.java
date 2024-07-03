package org.janelia.colormipsearch.cmd;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.dataexport.DataExporter;
import org.janelia.colormipsearch.cmd.dataexport.EMCDMatchesExporter;
import org.janelia.colormipsearch.cmd.dataexport.EMPPPMatchesExporter;
import org.janelia.colormipsearch.cmd.dataexport.ImageStoreKey;
import org.janelia.colormipsearch.cmd.dataexport.ImageStoreMapping;
import org.janelia.colormipsearch.cmd.dataexport.LMCDMatchesExporter;
import org.janelia.colormipsearch.cmd.dataexport.MIPsExporter;
import org.janelia.colormipsearch.cmd.dataexport.URLTransformer;
import org.janelia.colormipsearch.cmd.dataexport.ValidatingSerializerModifier;
import org.janelia.colormipsearch.cmd.jacsdata.CachedDataHelper;
import org.janelia.colormipsearch.cmd.jacsdata.JacsDataGetter;
import org.janelia.colormipsearch.cmd.jacsdata.PublishedDataGetter;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.dto.EMNeuronMetadata;
import org.janelia.colormipsearch.dto.LMNeuronMetadata;
import org.janelia.colormipsearch.model.FileType;

/**
 * This command is used to export data from the database to the file system in order to upload it to S3.
 */
class ExportData4NBCmd extends AbstractCmd {

    @Parameters(commandDescription = "Export neuron matches")
    static class ExportMatchesCmdArgs extends AbstractCmdArgs {

        @Parameter(names = {"--exported-result-type"}, required = true, // this is required because PPPs are handled a bit differently
                description = "Specifies neuron match type whether it's color depth search, PPP, etc.")
        ExportedResultType exportedResultType = ExportedResultType.EM_CD_MATCHES;

        @Parameter(names = {"--jacs-url", "--data-url"}, description = "JACS data service base URL")
        String dataServiceURL;

        @Parameter(names = {"--config-url"}, description = "Config URL that contains the library name mapping")
        String configURL = "https://config.int.janelia.org/config";

        @Parameter(names = {"--authorization"},
                description = "JACS authorization - this is the value of the authorization header")
        String authorization;

        @Parameter(names = {"--pctPositivePixels"}, description = "% of Positive PX Threshold (0-100%)")
        Double pctPositivePixels = 0.0;

        @Parameter(names = {"--ignore-grad-scores"},
                description = "Ignore gradient scores when selecting color depth matches",
                arity = 0)
        boolean ignoreGradScores = false;

        @Parameter(names = {"-as", "--alignment-space"}, description = "Alignment space")
        String alignmentSpace;

        @Parameter(names = {"--published-alignment-space-alias"}, description = "Alignment space aliases",
                converter = NameValueArg.NameArgConverter.class,
                variableArity = true)
        List<NameValueArg> publishedAlignmentSpaceAliases = new ArrayList<>();

        @Parameter(names = {"-l", "--library"}, description = "Library names from which mips or matches are selected for export",
                variableArity = true)
        List<String> libraries = new ArrayList<>();

        @Parameter(names = {"--target-library"}, description = "Target library names from which mips or matches are selected for export",
                variableArity = true)
        List<String> targetLibraries = new ArrayList<>();

        @Parameter(names = {"--exported-mips"}, description = "If set only export the specified MIPs", variableArity = true)
        List<String> exportedMIPIDs = new ArrayList<>();

        @Parameter(names = {"--exported-names"}, description = "If set only export the specified names", variableArity = true)
        List<String> exportedNames = new ArrayList<>();

        @Parameter(names = {"--subdir"}, description = "Results subdirectory for results")
        String subDir;

        @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
        int processingPartitionSize = 5000;

        @Parameter(names = {"--read-batch-size"}, description = "JACS read chunk size")
        int readBatchSize = 1000;

        @Parameter(names = {"--default-relative-url-index"},
                description = "default index value used to create the relative URLs")
        int defaultRelativeURLIndex = -1;

        @Parameter(names = {"--relative-url-indexes-by-filetype"},
                converter = NameValueArg.NameArgConverter.class,
                variableArity = true,
                description = "index values used to create the relative URLs for specific filetypes")
        List<NameValueArg> relativeURLIndexesByFileType = new ArrayList<>();

        @Parameter(names = {"--offset"}, description = "Offset of the exported data")
        long offset = 0;

        @Parameter(names = {"--size"}, description = "Size of the exported data")
        int size = 0;

        @Parameter(names = {"--neuron-tags"}, description = "Neuron tags to be exported", variableArity = true)
        List<String> neuronTags = new ArrayList<>();

        @Parameter(names = {"--excluded-neuron-tags"}, description = "Neuron tags to be excluded from export", variableArity = true)
        List<String> excludedNeuronTags = new ArrayList<>();

        @Parameter(names = {"--neuron-terms"}, description = "Neuron annotations to be exported", variableArity = true)
        List<String> neuronAnnotations = new ArrayList<>();

        @Parameter(names = {"--excluded-neuron-terms"}, description = "Neuron terms to be excluded from export", variableArity = true)
        List<String> excludedNeuronAnnotations = new ArrayList<>();

        @Parameter(names = {"--target-tags"}, description = "Target neuron tags to be exported",
                variableArity = true)
        List<String> targetNeuronTags = new ArrayList<>();

        @Parameter(names = {"--excluded-target-tags"}, description = "Target neuron tags to be excluded from export",
                variableArity = true)
        List<String> excludedTargetNeuronTags = new ArrayList<>();

        @Parameter(names = {"--target-terms"}, description = "Target neuron annotations to be exported",
                variableArity = true)
        List<String> targetNeuronAnnotations = new ArrayList<>();

        @Parameter(names = {"--excluded-target-terms"}, description = "Target neuron annotations to be excluded from export",
                variableArity = true)
        List<String> excludedTargetNeuronAnnotations = new ArrayList<>();

        @Parameter(names = {"--excluded-matches-tags"}, description = "Matches tags to be excluded from export", variableArity = true)
        List<String> excludedMatchesTags = new ArrayList<>();

        @Parameter(names = {"--default-image-store"}, description = "Default image store", required = true)
        String defaultImageStore;

        @Parameter(names = {"--image-stores-per-neuron-meta"},
                description = "Image stores per neuron metadata; the mapping must be based on the internal alignmentSpace and optionally library name;" +
                        "to define the store name based on the alignment space and library set the argument use both values separated by a comma like this: " +
                        "<as>,<libraryName>:<storeName>; to define the store based on alignment space use: <as>:<storeName>, " +
                        "e.g., JRC2018_Unisex_20x_HR,flyem_hemibrain_1_2_1:brain-store JRC2018_VNC_Unisex_40x_DS:vnc-store",
                converter = MultiKeyValueArg.MultiKeyValueArgConverter.class,
                variableArity = true)
        List<MultiKeyValueArg> imageStoresPerMetadata = new ArrayList<>();

        ExportMatchesCmdArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        @Nullable
        Path getOutputResultsDir() {
            return getOutputDirArg()
                    .map(dir -> StringUtils.isNotBlank(subDir) ? dir.resolve(subDir) : dir)
                    .orElse(null);
        }
    }

    private final ExportMatchesCmdArgs args;
    private final ObjectMapper mapper;

    ExportData4NBCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.args = new ExportMatchesCmdArgs(commonArgs);
            Validator validator = factory.getValidator();
            this.mapper = new ObjectMapper()
                    .registerModule(new SimpleModule().setSerializerModifier(new ValidatingSerializerModifier(validator)))
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            ;
        }
    }

    @Override
    ExportMatchesCmdArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        exportNeuronMatches();
    }

    private void exportNeuronMatches() {
        DataExporter dataExporter = getDataExporter();
        dataExporter.runExport();
    }

    private ScoresFilter getCDScoresFilter() {
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (args.pctPositivePixels > 0) {
            neuronsMatchScoresFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        if (!args.ignoreGradScores) {
            neuronsMatchScoresFilter.addSScore("gradientAreaGap", 0);
        }
        return neuronsMatchScoresFilter;
    }

    private ScoresFilter getPPPScoresFilter() {
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        neuronsMatchScoresFilter.addSScore("rank", 0);
        return neuronsMatchScoresFilter;
    }

    private DataExporter getDataExporter() {
        DaosProvider daosProvider = getDaosProvider();
        ItemsWriterToJSONFile itemsWriter = new ItemsWriterToJSONFile(
                args.commonArgs.noPrettyPrint
                        ? mapper.writer()
                        : mapper.writerWithDefaultPrettyPrinter());
        Map<String, Set<String>> publishedAlignmentSpaceAliases = args.publishedAlignmentSpaceAliases.stream()
                .collect(Collectors.toMap(
                        nv -> nv.argName,
                        nv -> ImmutableSet.copyOf(nv.argValues),
                        (v1s, v2s) -> ImmutableSet.<String>builder().addAll(v1s).addAll(v2s).build()));
        CachedDataHelper dataHelper = new CachedDataHelper(
                new JacsDataGetter(
                        args.dataServiceURL,
                        args.configURL,
                        args.authorization,
                        args.readBatchSize),
                new PublishedDataGetter(
                        daosProvider.getPublishedImageDao(),
                        daosProvider.getNeuronPublishedUrlsDao(),
                        publishedAlignmentSpaceAliases
                )
        );
        DataSourceParam dataSource = new DataSourceParam()
                .setAlignmentSpace(args.alignmentSpace)
                .addLibraries(args.libraries)
                .addTags(args.neuronTags)
                .addExcludedTags(args.excludedNeuronTags)
                .addAnnotations(args.neuronAnnotations)
                .addExcludedAnnotations(args.excludedNeuronAnnotations)
                .addNames(args.exportedNames)
                .addMipIDs(args.exportedMIPIDs)
                .setOffset(args.offset)
                .setSize(args.size);
        Executor exportsExecutor = CmdUtils.createCmdExecutor(args.commonArgs);
        ImageStoreMapping imageStoreMapping = new ImageStoreMapping(
                args.defaultImageStore,
                args.imageStoresPerMetadata.stream().collect(Collectors.toMap(
                        nv -> ImageStoreKey.fromList(nv.multiKey),
                        nv -> nv.value,
                        (s1, s2) -> s2 // resolve the conflict by returning the last value
                ))
        );
        URLTransformer urlTransformer = createURLTransformer();
        switch (args.exportedResultType) {
            case EM_CD_MATCHES:
                return new EMCDMatchesExporter(
                        dataHelper,
                        dataSource,
                        args.targetLibraries,
                        args.targetNeuronTags,
                        args.excludedTargetNeuronTags,
                        args.targetNeuronAnnotations,
                        args.excludedTargetNeuronAnnotations,
                        args.excludedMatchesTags,
                        getCDScoresFilter(),
                        urlTransformer,
                        imageStoreMapping,
                        args.getOutputResultsDir(),
                        exportsExecutor,
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getCDMatchesDao(),
                                "mipId"
                        ),
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case LM_CD_MATCHES:
                return new LMCDMatchesExporter(
                        dataHelper,
                        dataSource,
                        args.targetLibraries,
                        args.targetNeuronTags,
                        args.excludedTargetNeuronTags,
                        args.targetNeuronAnnotations,
                        args.excludedTargetNeuronAnnotations,
                        args.excludedMatchesTags,
                        getCDScoresFilter(),
                        urlTransformer,
                        imageStoreMapping,
                        args.getOutputResultsDir(),
                        exportsExecutor,
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getCDMatchesDao(),
                                "mipId"
                        ),
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case EM_PPP_MATCHES:
                return new EMPPPMatchesExporter(
                        dataHelper,
                        dataSource,
                        publishedAlignmentSpaceAliases,
                        getPPPScoresFilter(),
                        urlTransformer,
                        imageStoreMapping,
                        args.getOutputResultsDir(),
                        exportsExecutor,
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getPPPMatchesDao(),
                                "mipId"
                        ),
                        daosProvider.getPPPmUrlsDao(),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case EM_MIPS:
                return new MIPsExporter(
                        dataHelper,
                        dataSource,
                        urlTransformer,
                        imageStoreMapping,
                        args.getOutputResultsDir(),
                        exportsExecutor,
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize,
                        EMNeuronMetadata.class
                );
            case LM_MIPS:
                return new MIPsExporter(
                        dataHelper,
                        dataSource,
                        urlTransformer,
                        imageStoreMapping,
                        args.getOutputResultsDir(),
                        exportsExecutor,
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize,
                        LMNeuronMetadata.class
                );
            default:
                throw new IllegalArgumentException("Invalid export result type");
        }
    }

    private URLTransformer createURLTransformer() {
        Map<FileType, URLTransformer.URLTransformerParams> urlTransformParamsByFileType =
                args.relativeURLIndexesByFileType
                        .stream()
                        .collect(Collectors.toMap(
                            nv -> FileType.valueOf(nv.getArgName()),
                            nv -> {
                                int urlStartPos = Integer.parseInt(nv.getArgValues().get(0));
                                boolean changeNonHttpURIs;
                                if (nv.getArgValues().size() > 1) {
                                    changeNonHttpURIs = Boolean.parseBoolean(nv.getArgValues().get(1));
                                } else {
                                    changeNonHttpURIs = false;
                                }
                                return new URLTransformer.URLTransformerParams(urlStartPos, changeNonHttpURIs);
                            },
                            (v1, v2) -> v2
                        ));
        return new URLTransformer(
            args.defaultRelativeURLIndex,
            urlTransformParamsByFileType
        );
    }
}
