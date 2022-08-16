package org.janelia.colormipsearch.cmd;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.dataexport.DataExporter;
import org.janelia.colormipsearch.cmd.dataexport.MIPsExporter;
import org.janelia.colormipsearch.cmd.dataexport.PPPMatchesExporter;
import org.janelia.colormipsearch.cmd.dataexport.PerMaskCDMatchesExporter;
import org.janelia.colormipsearch.cmd.dataexport.PerTargetCDMatchesExporter;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This command is used to export data from the database to the file system in order to upload it to S3.
 */
class ExportNeuronMatchesCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ExportNeuronMatchesCmd.class);

    @Parameters(commandDescription = "Export neuron matches")
    static class ExportMatchesCmdArgs extends AbstractCmdArgs {

        @Parameter(names = {"--exported-result-type"}, required = true, // this is required because PPPs are handled a bit differently
                description = "Specifies neuron match type whether it's color depth search, PPP, etc.")
        ExportedResultType exportedResultType = ExportedResultType.PER_MASK_CDS_MATCHES;

        @Parameter(names = {"--pctPositivePixels"}, description = "% of Positive PX Threshold (0-100%)")
        Double pctPositivePixels = 0.0;

        @Parameter(names = {"--ignore-grad-scores"},
                description = "Ignore gradient scores when selecting color depth matches",
                arity = 0)
        boolean ignoreGradScores = false;

        @Parameter(names = {"-m", "--masks"}, description = "Masks library", converter = ListArg.ListArgConverter.class)
        ListArg masksLibrary;

        @Parameter(names = {"--perMaskSubdir"}, description = "Results subdirectory for results grouped by mask MIP ID")
        String perMaskSubdir;

        @Parameter(names = {"-i", "--targets"}, description = "Targets library", converter = ListArg.ListArgConverter.class)
        ListArg targetsLibrary;

        @Parameter(names = {"--perTargetSubdir"}, description = "Results subdirectory for results grouped by target MIP ID")
        String perTargetSubdir;

        @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
        int processingPartitionSize = 100;

        ExportMatchesCmdArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        @Nullable
        Path getPerMaskDir() {
            return getOutputDirArg()
                    .map(dir -> StringUtils.isNotBlank(perMaskSubdir) ? dir.resolve(perMaskSubdir) : dir)
                    .orElse(null);
        }

        @Nullable
        Path getPerTargetDir() {
            return getOutputDirArg()
                    .map(dir -> StringUtils.isNotBlank(perTargetSubdir) ? dir.resolve(perTargetSubdir) : dir)
                    .orElse(null);
        }
    }

    private final ExportMatchesCmdArgs args;
    private final ObjectMapper mapper;

    ExportNeuronMatchesCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new ExportMatchesCmdArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
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
        neuronsMatchScoresFilter.setEntityType(CDMatchEntity.class.getName());
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
        neuronsMatchScoresFilter.setEntityType(PPPMatchEntity.class.getName());
        neuronsMatchScoresFilter.addSScore("rank", 0);
        return neuronsMatchScoresFilter;
    }

    private DataExporter getDataExporter() {
        DaosProvider daosProvider = getDaosProvider();
        ItemsWriterToJSONFile itemsWriter = new ItemsWriterToJSONFile(
                args.commonArgs.noPrettyPrint
                        ? mapper.writer()
                        : mapper.writerWithDefaultPrettyPrinter());
        switch (args.exportedResultType) {
            case PER_MASK_CDS_MATCHES:
                return new PerMaskCDMatchesExporter(
                        ListArg.asDataSourceParam(args.masksLibrary),
                        getCDScoresFilter(),
                        args.getPerMaskDir(),
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getCDMatchesDao()
                        ),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case PER_TARGET_CDS_MATCHES:
                return new PerTargetCDMatchesExporter(
                        ListArg.asDataSourceParam(args.masksLibrary),
                        getCDScoresFilter(),
                        args.getPerMaskDir(),
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getCDMatchesDao()
                        ),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case PPP_MATCHES:
                return new PPPMatchesExporter(
                        ListArg.asDataSourceParam(args.masksLibrary),
                        getPPPScoresFilter(),
                        args.getPerMaskDir(),
                        new DBNeuronMatchesReader<>(
                                daosProvider.getNeuronMetadataDao(),
                                daosProvider.getPPPMatchesDao()
                        ),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case EM_MIPS:
                return new MIPsExporter(
                        null,
                        ListArg.asDataSourceParam(args.masksLibrary),
                        args.getPerMaskDir(),
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize
                );
            case LM_MIPS:
                return new MIPsExporter(
                        null,
                        ListArg.asDataSourceParam(args.targetsLibrary),
                        args.getPerTargetDir(),
                        daosProvider.getNeuronMetadataDao(),
                        itemsWriter,
                        args.processingPartitionSize
                );
            default:
                throw new IllegalArgumentException("Export result types must be specified");
        }
    }

}
