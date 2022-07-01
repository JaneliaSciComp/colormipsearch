package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cds.ColorDepthPixelMatchScore;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProviderFactory;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cmd.cdsprocess.ColorMIPSearchProcessor;
import org.janelia.colormipsearch.cmd.cdsprocess.LocalColorMIPSearchProcessor;
import org.janelia.colormipsearch.cmd.cdsprocess.SparkColorMIPSearchProcessor;
import org.janelia.colormipsearch.cmd.io.CDMIPsReader;
import org.janelia.colormipsearch.cmd.io.IOUtils;
import org.janelia.colormipsearch.cmd.io.JSONCDMIPsReader;
import org.janelia.colormipsearch.cmd.io.JSONCDSResultsWriter;
import org.janelia.colormipsearch.cmd.io.ResultMatchesWriter;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDSMatch;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColorDepthSearchCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ColorDepthSearchCmd.class);

    @Parameters(commandDescription = "Color depth search for a batch of MIPs")
    static class ColorDepthSearchArgs extends AbstractColorDepthMatchArgs {

        @Parameter(names = {"--images", "-i"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Comma-delimited list of JSON configs containing images to search")
        List<ListArg> librariesInputs;

        @Parameter(names = {"--images-index"}, description = "Input image file(s) start index")
        long librariesStartIndex;

        @Parameter(names = {"--images-length"}, description = "Input image file(s) length")
        int librariesLength;

        @Parameter(names = {"--masks", "-m"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Image file(s) to use as the search masks")
        List<ListArg> masksInputs;

        @Parameter(names = {"--masks-index"}, description = "Mask file(s) start index")
        long masksStartIndex;

        @Parameter(names = {"--masks-length"}, description = "Mask file(s) length")
        int masksLength;

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
        CDMIPsReader cdmiPsReader = new JSONCDMIPsReader(mapper);
        ColorMIPSearchProcessor<M, T> colorMIPSearchProcessor;
        ColorDepthSearchAlgorithmProvider<ColorDepthPixelMatchScore> cdsAlgorithmProvider;
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
                args.librariesInputs,
                args.librariesStartIndex, args.librariesLength,
                args.libraryMIPsFilter);
        if (maskMips.isEmpty() || targetMips.isEmpty()) {
            LOG.info("Nothing to do for {} masks and {} targets", maskMips.size(), targetMips.size());
            return;
        }
        // save CDS parameters
        String masksInputs = inputNames(args.masksInputs);
        String targetInputs = inputNames(args.librariesInputs);
        saveCDSParameters(colorMIPSearch,
                args.getOutputDir(),
                "masks-" + masksInputs + "-inputs-" + targetInputs + "-cdsParameters.json");
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
            List<CDSMatch<M, T>> cdsResults = colorMIPSearchProcessor.findAllColorDepthMatches(maskMips, targetMips);
            ResultMatchesWriter<M, T, CDSMatch<M, T>> cdsResultsWriter = new JSONCDSResultsWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    args.getPerMaskDir(),
                    args.getPerLibraryDir()
            );
            cdsResultsWriter.write(cdsResults);
        } finally {
            colorMIPSearchProcessor.terminate();
        }
    }

    private ImageArray<?> loadQueryROIMask(String queryROIMask) {
        if (StringUtils.isBlank(queryROIMask)) {
            return null;
        } else {
            return NeuronMIPUtils.loadImageFromFileData(FileData.fromString(queryROIMask));
        }
    }

    private String inputNames(List<ListArg> mipsArg) {
        return mipsArg.stream()
                .map(ListArg::listArgName)
                .reduce("", (l1, l2) -> StringUtils.isBlank(l1) ? l2 : l1 + "-" + l2);
    }

    private List<? extends AbstractNeuronMetadata> readMIPs(CDMIPsReader mipsReader,
                                                            List<ListArg> mipsArg, long startIndexArg, int length,
                                                            Set<String> filter) {
        long startIndex = startIndexArg > 0 ? startIndexArg : 0;
        List<? extends AbstractNeuronMetadata> allMips = mipsArg.stream()
                .flatMap(libraryInput -> mipsReader.readMIPs(
                        libraryInput.input,
                        libraryInput.offset,
                        libraryInput.length).stream())
                .filter(neuronMetadata -> CollectionUtils.isEmpty(filter) ||
                        filter.contains(neuronMetadata.getPublishedName().toLowerCase()) ||
                        filter.contains(neuronMetadata.getId()))
                .skip(startIndex)
                .collect(Collectors.toList());
        return length > 0 && length < allMips.size()
                ? allMips.subList(0, length)
                : allMips;
    }

    private void saveCDSParameters(ColorMIPSearch colorMIPSearch, Path outputDir, String fname) {
        File outputFile;
        if (outputDir != null && StringUtils.isNotBlank(fname)) {
            IOUtils.createDirs(outputDir);
            outputFile = outputDir.resolve(fname).toFile();
        } else {
            outputFile = null;
        }
        try {
            if (outputFile != null) {
                mapper.writerWithDefaultPrettyPrinter().
                        writeValue(outputFile, colorMIPSearch.getCDSParameters());
            } else {
                mapper.writerWithDefaultPrettyPrinter().
                        writeValue(System.out, colorMIPSearch.getCDSParameters());
            }
        } catch (IOException e) {
            LOG.error("Error persisting color depth search parameters to {}", outputFile, e);
            throw new IllegalStateException(e);
        }

    }
}