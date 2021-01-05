package org.janelia.colormipsearch.cmd;

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.apache.commons.lang3.StringUtils;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class Main {

    private static class MainArgs {
        @Parameter(names = "--cacheSize", description = "Max cache size")
        long cacheSize = 0L;
        @Parameter(names = "--cacheExpirationInSeconds", description = "Cache expiration in seconds")
        long cacheExpirationInSeconds = 300;
        @Parameter(names = "-h", description = "Display the help message", help = true, arity = 0)
        boolean displayHelpMessage = false;
    }

    public static void main(String[] argv) {
        MainArgs mainArgs = new MainArgs();
        CommonArgs commonArgs = new CommonArgs();
        AbstractCmd[] cmds = new AbstractCmd[] {
                new CreateColorDepthSearchJSONInputCmd("createColorDepthSearchJSONInput", commonArgs),
                new GroupMIPsByPublishedNameCmd("groupMIPsByPublishedName", commonArgs),
                new ReplaceMIPsMetadataAttributesCmd("replaceAttributes", commonArgs),
                new ColorDepthSearchJSONInputCmd(
                        "searchFromJSON",
                        commonArgs,
                        () -> mainArgs.cacheSize,
                        () -> mainArgs.cacheExpirationInSeconds,
                        false),
                new ColorDepthSearchLocalMIPsCmd(
                        "searchLocalFiles",
                        commonArgs,
                        () -> mainArgs.cacheSize,
                        () -> mainArgs.cacheExpirationInSeconds,
                        false),
                new CalculateNegativeScoresCmd(
                        "gradientScore",
                        commonArgs,
                        () -> mainArgs.cacheSize,
                        () -> mainArgs.cacheExpirationInSeconds),
                new UpdateGradientScoresFromReverseSearchResultsCmd(
                        "gradientScoresFromMatchedResults",
                        commonArgs),
                new MergeResultsCmd("mergeResults", commonArgs),
                new NormalizeScoresCmd("normalizeScores", commonArgs),
                new CopyColorDepthMIPVariantsCmd("copyMIPSegmentation", commonArgs)
        };
        JCommander.Builder cmdlineBuilder = JCommander.newBuilder()
                .addObject(mainArgs);
        for (AbstractCmd cmd : cmds) {
            cmdlineBuilder.addCommand(cmd.getCommandName(), cmd.getArgs());
        }
        JCommander cmdline = cmdlineBuilder.build();

        try {
            cmdline.parse(argv);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder(e.getMessage()).append('\n');
            if (StringUtils.isNotBlank(cmdline.getParsedCommand())) {
                cmdline.getUsageFormatter().usage(cmdline.getParsedCommand(), sb);
            } else {
                cmdline.getUsageFormatter().usage(sb);
            }
            cmdline.getConsole().println(sb.toString());
            System.exit(1);
        }

        if (mainArgs.displayHelpMessage) {
            cmdline.usage();
            System.exit(0);
        } else if (commonArgs.displayHelpMessage && StringUtils.isNotBlank(cmdline.getParsedCommand())) {
            StringBuilder sb = new StringBuilder();
            cmdline.getUsageFormatter().usage(cmdline.getParsedCommand(), sb);
            cmdline.getConsole().println(sb.toString());
            System.exit(0);
        } else if (StringUtils.isBlank(cmdline.getParsedCommand())) {
            StringBuilder sb = new StringBuilder("Missing command\n");
            cmdline.getUsageFormatter().usage(sb);
            cmdline.getConsole().println(sb.toString());
            System.exit(1);
        }
        // invoke the appropriate command
        for (AbstractCmd cmd : cmds) {
            if (cmd.matches(cmdline.getParsedCommand())) {
                List<String> validationErrors = cmd.getArgs().validate();
                if (!validationErrors.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    validationErrors.forEach(err -> sb.append(err).append('\n'));
                    cmdline.getUsageFormatter().usage(cmdline.getParsedCommand(), sb);
                    cmdline.getConsole().println(sb.toString());
                    System.exit(1);
                }
                cmd.execute();
                return;
            }
        }
        StringBuilder sb = new StringBuilder("Invalid command\n");
        cmdline.getUsageFormatter().usage(sb);
        cmdline.getConsole().println(sb.toString());
        System.exit(1);
    }
}
