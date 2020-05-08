package org.janelia.colormipsearch;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NormalizeGradientScoresCmd {
    private static final Logger LOG = LoggerFactory.getLogger(NormalizeGradientScoresCmd.class);

    @Parameters(commandDescription = "Normalize gradient score for the search results - " +
            "if the area gap is not available consider it as if there was a perfect shape match, i.e., areagap = 0")
    static class NormalizeGradientScoresArgs {
        @Parameter(names = {"--resultsDir", "-rd"}, converter = ListArg.ListArgConverter.class,
                description = "Results directory for which scores will be normalized")
        private ListArg resultsDir;

        @Parameter(names = {"--resultsFile", "-rf"}, variableArity = true,
                description = "Files for which results will be normalized")
        private List<String> resultsFiles;

        @Parameter(names = {"--pctPositivePixels"}, description = "% of Positive PX Threshold (0-100%)")
        private Double pctPositivePixels = 0.0;

        @Parameter(names = "-cleanup", description = "Cleanup results and remove fields not necessary in productiom", arity = 0)
        private boolean cleanup = false;

        @ParametersDelegate
        final CommonArgs commonArgs;

        NormalizeGradientScoresArgs(CommonArgs commonArgs) {
            this.commonArgs = commonArgs;
        }

        Path getOutputDir() {
            if (StringUtils.isNotBlank(commonArgs.outputDir)) {
                return Paths.get(commonArgs.outputDir);
            } else {
                return null;
            }
        }

        boolean validate() {
            return resultsDir != null || CollectionUtils.isNotEmpty(resultsFiles);
        }
    }

    private final NormalizeGradientScoresArgs args;
    private final ObjectMapper mapper;

    NormalizeGradientScoresCmd(CommonArgs commonArgs) {
        args =  new NormalizeGradientScoresArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    NormalizeGradientScoresArgs getArgs() {
        return args;
    }

    void execute() {
        setGradientScores(args);
    }

    private void setGradientScores(NormalizeGradientScoresArgs args) {
        List<String> filesToProcess;
        if (CollectionUtils.isNotEmpty(args.resultsFiles)) {
            filesToProcess = args.resultsFiles;
        } else if (args.resultsDir != null) {
            try {
                int from = Math.max(args.resultsDir.offset, 0);
                int length = args.resultsDir.length;
                List<String> filenamesList = Files.find(Paths.get(args.resultsDir.input), 1, (p, fa) -> fa.isRegularFile())
                        .skip(from)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                if (length > 0 && length < filenamesList.size()) {
                    filesToProcess = filenamesList.subList(0, length);
                } else {
                    filesToProcess = filenamesList;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            filesToProcess = Collections.emptyList();
        }
        filesToProcess.stream().parallel().forEach((fn) -> {
            LOG.info("Set gradient score results for {}", fn);
            File cdsFile = new File(fn);
            Results<List<ColorMIPSearchResultMetadata>> cdsResults = CmdUtils.readCDSResultsFromJSONFile(cdsFile , mapper);
            Long maxAreaGap = cdsResults.results.stream()
                    .map(ColorMIPSearchResultMetadata::getGradientAreaGap)
                    .max(Long::compare)
                    .orElse(-1L);
            LOG.debug("Max area gap for {}  -> {}", fn, maxAreaGap);
            Integer maxMatchingPixels = cdsResults.results.stream()
                    .map(ColorMIPSearchResultMetadata::getMatchingPixels)
                    .max(Integer::compare)
                    .orElse(0);
            LOG.debug("Max pixel match for {}  -> {}", fn, maxMatchingPixels);
            List<ColorMIPSearchResultMetadata> cdsResultsWithNormalizedScore = cdsResults.results.stream()
                    .filter(csr -> csr.getMatchingPixelsPct() * 100. > args.pctPositivePixels)
                    .peek(csr -> {
                        long areaGap = csr.getGradientAreaGap();
                        double normalizedGapScore = GradientAreaGapUtils.calculateAreaGapScore(
                                csr.getGradientAreaGap(), maxAreaGap, csr.getMatchingPixels(), csr.getMatchingPixelsPct(), maxMatchingPixels
                        );
                        if (areaGap >= 0) {
                            csr.setNormalizedGradientAreaGapScore(normalizedGapScore);
                        } else {
                            csr.setArtificialShapeScore(normalizedGapScore);
                        }
                    })
                    .collect(Collectors.toList());
            CmdUtils.sortCDSResults(cdsResultsWithNormalizedScore);
            CmdUtils.writeCDSResultsToJSONFile(new Results<>(cdsResultsWithNormalizedScore), CmdUtils.getOutputFile(args.getOutputDir(), new File(fn)), mapper);
        });
    }

}
