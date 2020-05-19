package org.janelia.colormipsearch.cmd;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.CachedMIPsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static class MainArgs {
        @Parameter(names = "--cacheSize", description = "Max cache size")
        private long cacheSize = 200000L;
        @Parameter(names = "--cacheExpirationInMin", description = "Cache expiration in minutes")
        private long cacheExpirationInMin = 60;
        @Parameter(names = "-h", description = "Display the help message", help = true, arity = 0)
        private boolean displayHelpMessage = false;
    }

    public static void main(String[] argv) {
        MainArgs mainArgs = new MainArgs();
        CommonArgs commonArgs = new CommonArgs();
        ColorDepthSearchJSONInputCmd jsonMIPsSearchCmd = new ColorDepthSearchJSONInputCmd(commonArgs);
        ColorDepthSearchLocalMIPsCmd localMIPFilesSearchCmd = new ColorDepthSearchLocalMIPsCmd(commonArgs);
        MergeResultsCmd mergeResultsCmd = new MergeResultsCmd(commonArgs);
        NormalizeGradientScoresCmd normalizeGradientScoresCmd = new NormalizeGradientScoresCmd(commonArgs);
        CalculateGradientScoresCmd calculateGradientScoresCmd = new CalculateGradientScoresCmd(commonArgs);
        ReplaceURLsCmd replaceURLsCmd = new ReplaceURLsCmd(commonArgs);
        UpdateGradientScoresFromReverseSearchResultsCmd updateGradientScoresFromReverseSearchResultsCmd = new UpdateGradientScoresFromReverseSearchResultsCmd(commonArgs);

        JCommander cmdline = JCommander.newBuilder()
                .addObject(mainArgs)
                .addCommand("searchFromJSON", jsonMIPsSearchCmd.getArgs())
                .addCommand("searchLocalFiles", localMIPFilesSearchCmd.getArgs())
                .addCommand("mergeResults", mergeResultsCmd.getArgs())
                .addCommand("gradientScore", calculateGradientScoresCmd.getArgs())
                .addCommand("normalizeGradientScores", normalizeGradientScoresCmd.getArgs())
                .addCommand("replaceImageURLs", replaceURLsCmd.getArgs())
                .addCommand("gradientScoresFromMatchedResults", updateGradientScoresFromReverseSearchResultsCmd.getArgs())
                .build();

        try {
            cmdline.parse(argv);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder(e.getMessage()).append('\n');
            if (StringUtils.isNotBlank(cmdline.getParsedCommand())) {
                cmdline.usage(cmdline.getParsedCommand(), sb);
            } else {
                cmdline.usage(sb);
            }
            JCommander.getConsole().println(sb.toString());
            System.exit(1);
        }

        if (mainArgs.displayHelpMessage) {
            cmdline.usage();
            System.exit(0);
        } else if (commonArgs.displayHelpMessage && StringUtils.isNotBlank(cmdline.getParsedCommand())) {
            cmdline.usage(cmdline.getParsedCommand());
            System.exit(0);
        } else if (StringUtils.isBlank(cmdline.getParsedCommand())) {
            StringBuilder sb = new StringBuilder("Missing command\n");
            cmdline.usage(sb);
            JCommander.getConsole().println(sb.toString());
            System.exit(1);
        }
        // initialize the cache
        CachedMIPsUtils.initializeCache(mainArgs.cacheSize, mainArgs.cacheExpirationInMin);
        // invoke the appropriate command
        switch (cmdline.getParsedCommand()) {
            case "searchFromJSON":
                CmdUtils.createOutputDirs(jsonMIPsSearchCmd.getArgs().getPerLibraryDir(), jsonMIPsSearchCmd.getArgs().getPerMaskDir());
                jsonMIPsSearchCmd.execute();
                break;
            case "searchLocalFiles":
                CmdUtils.createOutputDirs(localMIPFilesSearchCmd.getArgs().getPerLibraryDir(), localMIPFilesSearchCmd.getArgs().getPerMaskDir());
                localMIPFilesSearchCmd.execute();
                break;
            case "gradientScore":
                if (calculateGradientScoresCmd.getArgs().getResultsDir() == null && calculateGradientScoresCmd.getArgs().getResultsFile() == null) {
                    StringBuilder sb = new StringBuilder("No result file or directory containing results has been specified").append('\n');
                    cmdline.usage(cmdline.getParsedCommand(), sb);
                    System.exit(1);
                }
                CmdUtils.createOutputDirs(calculateGradientScoresCmd.getArgs().getOutputDir());
                calculateGradientScoresCmd.execute();
                break;
            case "mergeResults":
                if (CollectionUtils.isEmpty(mergeResultsCmd.getArgs().resultsDirs) &&
                        CollectionUtils.isEmpty(mergeResultsCmd.getArgs().resultsFiles)) {
                    StringBuilder sb = new StringBuilder("No result file or directory containing results has been specified").append('\n');
                    cmdline.usage(cmdline.getParsedCommand(), sb);
                    System.exit(1);
                }
                CmdUtils.createOutputDirs(mergeResultsCmd.getArgs().getOutputDir());
                mergeResultsCmd.execute();
                break;
            case "normalizeGradientScores":
                if (!normalizeGradientScoresCmd.getArgs().validate()) {
                    StringBuilder sb = new StringBuilder("No result file or directory containing results has been specified").append('\n');
                    cmdline.usage(cmdline.getParsedCommand(), sb);
                    System.exit(1);
                }
                CmdUtils.createOutputDirs(normalizeGradientScoresCmd.getArgs().getOutputDir());
                normalizeGradientScoresCmd.execute();
                break;
            case "replaceImageURLs":
                if (!replaceURLsCmd.getArgs().validate()) {
                    StringBuilder sb = new StringBuilder("No result file or directory containing results has been specified").append('\n');
                    cmdline.usage(cmdline.getParsedCommand(), sb);
                    System.exit(1);
                }
                CmdUtils.createOutputDirs(replaceURLsCmd.getArgs().getOutputDir());
                replaceURLsCmd.execute();
                break;
            case "gradientScoresFromMatchedResults":
                if (!updateGradientScoresFromReverseSearchResultsCmd.getArgs().validate()) {
                    StringBuilder sb = new StringBuilder("No result file or directory containing results has been specified").append('\n');
                    cmdline.usage(cmdline.getParsedCommand(), sb);
                    System.exit(1);
                }
                CmdUtils.createOutputDirs(updateGradientScoresFromReverseSearchResultsCmd.getArgs().getOutputDir());
                updateGradientScoresFromReverseSearchResultsCmd.execute();
                break;
            default:
                StringBuilder sb = new StringBuilder("Invalid command\n");
                cmdline.usage(sb);
                JCommander.getConsole().println(sb.toString());
                System.exit(1);
        }
    }
}