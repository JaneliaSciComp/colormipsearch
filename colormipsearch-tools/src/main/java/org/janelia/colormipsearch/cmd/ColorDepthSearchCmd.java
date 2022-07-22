package org.janelia.colormipsearch.cmd;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import org.janelia.colormipsearch.dataio.CDMIPsReader;
import org.janelia.colormipsearch.dataio.CDSParamsWriter;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.PartitionedNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCDMIPsReader;
import org.janelia.colormipsearch.dataio.db.DBCDScoresOnlyWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONCDMIPsReader;
import org.janelia.colormipsearch.dataio.fs.JSONCDSParamsWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command that runs the Color Depth Search.
 */
public class ColorDepthSearchCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ColorDepthSearchCmd.class);

    @Parameters(commandDescription = "Color depth search for a batch of MIPs")
    static class ColorDepthSearchArgs extends AbstractColorDepthMatchArgs {

        @Parameter(names = {"--mips-storage"},
                description = "Specifies MIPs storage")
        StorageType mipsStorage = StorageType.DB;

        @Parameter(names = {"--always-new-matches"},
                description = "If set a new color depth search run will always create new results; " +
                        "the default behavior is to update entries that match same images", arity = 0)
        boolean alwaysNewMatches = false;

        @Parameter(names = {"--masks", "-m"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Image file(s) to use as the search masks")
        List<ListArg> masksInputs;

        @Parameter(names = {"--masks-index"}, description = "Mask file(s) start index")
        long masksStartIndex;

        @Parameter(names = {"--masks-length"}, description = "Mask file(s) length")
        int masksLength;

        @Parameter(names = {"--targets", "-i"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Comma-delimited list of JSON configs containing images to search")
        List<ListArg> targetsInputs;

        @Parameter(names = {"--targets-index"}, description = "Input image file(s) start index")
        long targetsStartIndex;

        @Parameter(names = {"--targets-length"}, description = "Input image file(s) length")
        int targetsLength;

        public ColorDepthSearchArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

    }

    private final ColorDepthSearchArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final boolean useSpark;
    private final ObjectMapper mapper;

    ColorDepthSearchCmd(String commandName,
                        CommonArgs commonArgs,
                        Supplier<Long> cacheSizeSupplier,
                        boolean useSpark) {
        super(commandName);
        this.args = new ColorDepthSearchArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
        this.useSpark = useSpark;
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

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> void runColorDepthSearch() {
        CDMIPsReader cdmiPsReader = getCDMipsReader();
        ColorMIPSearchProcessor<M, T> colorMIPSearchProcessor;
        ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider;
        ImageRegionDefinition excludedRegions = args.getRegionGeneratorForTextLabels();
        cdsAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createPixMatchCDSAlgorithmProvider(
                args.mirrorMask,
                args.dataThreshold,
                args.pixColorFluctuation,
                args.xyShift,
                excludedRegions
        );
        ColorMIPSearch colorMIPSearch = new ColorMIPSearch(args.pctPositivePixels, args.maskThreshold, cdsAlgorithmProvider);
        @SuppressWarnings("unchecked")
        List<M> maskMips = (List<M>) readMIPs(cdmiPsReader,
                args.masksInputs,
                args.masksStartIndex, args.masksLength,
                args.maskMIPsFilter);
        @SuppressWarnings("unchecked")
        List<T> targetMips = (List<T>) readMIPs(cdmiPsReader,
                args.targetsInputs,
                args.targetsStartIndex, args.targetsLength,
                args.libraryMIPsFilter);
        if (maskMips.isEmpty() || targetMips.isEmpty()) {
            LOG.info("Nothing to do for {} masks and {} targets", maskMips.size(), targetMips.size());
            return;
        }
        // save CDS parameters
        getCDSParamsWriter().writeParams(
                args.masksInputs.stream().map(ListArg::asDataSourceParam).collect(Collectors.toList()),
                args.targetsInputs.stream().map(ListArg::asDataSourceParam).collect(Collectors.toList()),
                colorMIPSearch.getCDSParameters());
        if (useSpark) {
            colorMIPSearchProcessor = new SparkColorMIPSearchProcessor<>(
                    args.appName,
                    colorMIPSearch,
                    args.processingPartitionSize
            );
        } else {
            colorMIPSearchProcessor = new LocalColorMIPSearchProcessor<>(
                    colorMIPSearch,
                    args.processingPartitionSize,
                    CmdUtils.createCmdExecutor(args.commonArgs)
            );
        }
        try {
            List<CDMatch<M, T>> cdsResults = colorMIPSearchProcessor.findAllColorDepthMatches(maskMips, targetMips);
            NeuronMatchesWriter<M, T, CDMatch<M, T>> cdsResultsWriter = getCDSMatchesWriter();
            cdsResultsWriter.write(cdsResults);
        } finally {
            colorMIPSearchProcessor.terminate();
        }
    }

    private CDMIPsReader getCDMipsReader() {
        if (args.mipsStorage == StorageType.DB) {
            return new DBCDMIPsReader(getConfig());
        } else {
            return new JSONCDMIPsReader(mapper);
        }
    }

    private CDSParamsWriter getCDSParamsWriter() {
        return new JSONCDSParamsWriter(
                args.getOutputDir(),
                mapper);
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> NeuronMatchesWriter<M, T, CDMatch<M, T>>
    getCDSMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            if (args.alwaysNewMatches) {
                // always create new matches
                return new DBNeuronMatchesWriter<>(getConfig());
            } else {
                // only update the scores if a match exists
                // since this writes items one at a time - partition and process partitions in parallel
                return new PartitionedNeuronMatchesWriter<>(
                        new DBCDScoresOnlyWriter<>(getConfig()),
                        args.processingPartitionSize,
                        true
                );
            }
        } else {
            return new JSONNeuronMatchesWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    AbstractNeuronMetadata::getId, // group results by neuron MIP ID
                    Comparator.comparingDouble(m -> -(((CDMatch<?,?>) m).getMatchingPixels())), // descending order by matching pixels
                    args.getPerMaskDir(),
                    args.getPerTargetDir()
            );
        }
    }

    private List<? extends AbstractNeuronMetadata> readMIPs(CDMIPsReader mipsReader,
                                                            List<ListArg> mipsArg, long startIndexArg, int length,
                                                            Set<String> filter) {
        long startIndex = startIndexArg > 0 ? startIndexArg : 0;
        List<? extends AbstractNeuronMetadata> allMips = mipsArg.stream()
                .flatMap(libraryInput -> mipsReader.readMIPs(ListArg.asDataSourceParam(libraryInput)).stream())
                .filter(neuronMetadata -> CollectionUtils.isEmpty(filter) ||
                        filter.contains(neuronMetadata.getPublishedName().toLowerCase()) ||
                        filter.contains(neuronMetadata.getId()))
                .skip(startIndex)
                .collect(Collectors.toList());
        return length > 0 && length < allMips.size()
                ? allMips.subList(0, length)
                : allMips;
    }
}
