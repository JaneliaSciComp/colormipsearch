package org.janelia.colormipsearch.cmd;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.api_v2.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportPPPResultsCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(ImportPPPResultsCmd.class);

    @Parameters(commandDescription = "Convert the original PPP results into NeuronBridge compatible results")
    static class CreatePPPResultsArgs extends AbstractCmdArgs {
        @Parameter(names = {"--jacs-url", "--data-url"}, variableArity = true,
                description = "JACS data service base URL")
        List<String> dataServiceURLs;

        @Parameter(names = {"--authorization"}, description = "JACS authorization - this is the value of the authorization header")
        String authorization;

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space")
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        @Parameter(names = {"--anatomical-area", "-area"}, description = "Anatomical area")
        String anatomicalArea = "Brain";

        @Parameter(names = {"--em-dataset"}, description = "EM Dataset; this is typically hemibrain or vnc")
        String emDataset = "hemibrain";

        @Parameter(names = {"--em-dataset-version"}, description = "EM Dataset version")
        String emDatasetVersion = "1.2.1";

        @Parameter(names = {"--results-dir", "-rd"}, converter = ListArg.ListArgConverter.class,
                description = "Location of the original PPP results")
        private ListArg resultsDir;

        @Parameter(names = {"--results-file", "-rf"}, variableArity = true,
                description = "File(s) containing original PPP results.")
        private List<String> resultsFiles;

        @Parameter(names = "--neuron-matches-sub-dir", description = "The name of the neuron sub-directory containing the actual JSON results." +
                "For example - if the parameter is set to 'lm_cable_length_20_v4_adj_by_cov_numba_agglo_aT' each neuron folder will contain a" +
                "'lm_cable_length_20_v4_adj_by_cov_numba_agglo_aT' subdirectory that holds the JSON results generated by the PPP process")
        private String neuronMatchesSubDirName = "lm_cable_length_20_v4_adj_by_cov_numba_agglo_aT";

        @Parameter(names = "--matches-prefix", description = "The prefix of the JSON results file containing the scores for the PPP matches")
        private String jsonPPPResultsPrefix = "cov_scores_";

        @Parameter(names = "--screenshots-dir", description = "The prefix of the JSON results file containing the images for the top 500 PPP matches for each neuron.")
        private String screenshotsDir = "screenshots";

        @Parameter(names = {"--only-best-skeleton-matches"}, description = "Include only best skeleton matches", arity = 0)
        boolean onlyBestSkeletonMatches = false;

        @Parameter(names = {"--jacs-read-batch-size"}, description = "Batch size for getting data from JACS")
        int jacsReadBatchSize = 5000;

        @Parameter(names = {"--processing-partition-size", "-ps"}, description = "Processing partition size")
        int processingPartitionSize = 500;

        CreatePPPResultsArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final CreatePPPResultsArgs args;

    public ImportPPPResultsCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new CreatePPPResultsArgs(commonArgs);
    }

    @Override
    CreatePPPResultsArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        CmdUtils.createDirs(args.getOutputDir());
        importPPPResults();
    }

    private void importPPPResults() {
        long startTime = System.currentTimeMillis();
        Stream<Path> filesToProcess;
        if (CollectionUtils.isNotEmpty(args.resultsFiles)) {
            filesToProcess = args.resultsFiles.stream().map(Paths::get);
        } else {
            Stream<Path> allDirsWithPPPResults = streamDirsWithPPPResults(args.resultsDir.getInputPath());
            Stream<Path> dirsToProcess;
            int offset = Math.max(0, args.resultsDir.offset);
            if (args.resultsDir.length > 0) {
                dirsToProcess = allDirsWithPPPResults.skip(offset).limit(args.resultsDir.length);
            } else {
                dirsToProcess = allDirsWithPPPResults.skip(offset);
            }
            filesToProcess = dirsToProcess.flatMap(d -> getPPPResultsFromDir(d).stream());
        }
        Utils.processPartitionStream(
                filesToProcess.parallel(),
                args.processingPartitionSize,
                this::processPPPFiles);
        LOG.info("Processed all files in {}s", (System.currentTimeMillis() - startTime) / 1000.);
    }

    private void processPPPFiles(List<Path> listOfPPPResults) {
        // FIXME
    }

    private Stream<Path> streamDirsWithPPPResults(Path startPath) {
        try {
            Stream.Builder<Path> builder = Stream.builder();
            Files.walkFileTree(startPath, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String name = dir.getFileName().toString();
                    if (name.equals(args.neuronMatchesSubDirName)) {
                        builder.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    } else if (name.startsWith("nblastScores") || name.equals("screenshots")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
            return builder.build();
        } catch (IOException e) {
            LOG.error("Error traversing {}", startPath, e);
            return Stream.empty();
        }
    }

    private List<Path> getPPPResultsFromDir(Path pppResultsDir) {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pppResultsDir, args.jsonPPPResultsPrefix + "*.json")) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        // filter out files <prefix><neuron>_01.json or <prefix><neuron>_02.json
                        String neuronName = fn
                                .replaceAll("(_\\d+)?\\.json$", "")
                                .replaceAll(args.jsonPPPResultsPrefix, "");
                        return fn.equals(args.jsonPPPResultsPrefix + neuronName + ".json");
                    })
                    .collect(Collectors.toList())
                    ;
        } catch (IOException e) {
            LOG.error("Error getting PPP JSON result file names from {}", pppResultsDir, e);
            return Collections.emptyList();
        }
    }

}