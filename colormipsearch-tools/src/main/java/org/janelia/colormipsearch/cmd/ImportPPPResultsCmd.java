package org.janelia.colormipsearch.cmd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.ws.rs.client.Client;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.jacsdata.CDMIPBody;
import org.janelia.colormipsearch.cmd.jacsdata.CDMIPSample;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.janelia.colormipsearch.ppp.RawPPPMatchesReader;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class ImportPPPResultsCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(ImportPPPResultsCmd.class);
    private static final Random RAND = new Random();

    @Parameters(commandDescription = "Convert the original PPP results into NeuronBridge compatible results")
    static class CreatePPPResultsArgs extends AbstractCmdArgs {
        // This is a multi value argument because if I need to distribute it
        // I would like to do some kind of load balnacing and have different instances
        // spread the requests accross multiple servers
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
        int jacsReadBatchSize = 10000;

        @Parameter(names = {"--processing-partition-size", "-ps"}, description = "Processing partition size")
        int processingPartitionSize = 1000;

        CreatePPPResultsArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        boolean hasDataServiceURL() {
            return CollectionUtils.isNotEmpty(dataServiceURLs);
        }
    }

    private final CreatePPPResultsArgs args;
    private final ObjectMapper mapper;
    private final RawPPPMatchesReader rawPPPMatchesReader;

    ImportPPPResultsCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new CreatePPPResultsArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.rawPPPMatchesReader = new RawPPPMatchesReader();
    }

    @Override
    CreatePPPResultsArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
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
        ItemsHandling.processPartitionStream(
                filesToProcess,
                args.processingPartitionSize,
                this::processPPPFiles,
                true);
        LOG.info("Processed all files in {}s", (System.currentTimeMillis() - startTime) / 1000.);
    }

    private void processPPPFiles(List<Path> listOfPPPResults) {
        long start = System.currentTimeMillis();
        NeuronMatchesWriter<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> pppMatchesWriter = getPPPMatchesWriter();
        listOfPPPResults.stream()
                .peek(fp -> MDC.put("PPPFile", fp.getFileName().toString()))
                .map(this::importPPPRResultsFromFile)
                .forEach(inputPPPMatches -> {
                    writePPPMatches(inputPPPMatches, pppMatchesWriter);
                    MDC.remove("PPPFile");
                });
        LOG.info("Processed {} PPP results in {}s", listOfPPPResults.size(), (System.currentTimeMillis() - start) / 1000.);
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

    /**
     * Import PPP results from a list of file matches that are all for the same neuron.
     *
     * @param pppResultsFile
     * @return
     */
    private List<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> importPPPRResultsFromFile(Path pppResultsFile) {
        List<InputPPPMatch> inputPPPMatches = rawPPPMatchesReader.readPPPMatches(
                        pppResultsFile.toString(), args.onlyBestSkeletonMatches)
                .map(this::fillInNeuronMetadata)
                .collect(Collectors.toList());
        Set<String> matchedLMSampleNames = inputPPPMatches.stream()
                .map(InputPPPMatch::getLmSampleName)
                .collect(Collectors.toSet());
        Set<String> neuronNames = inputPPPMatches.stream()
                .map(InputPPPMatch::getEmNeuronName)
                .collect(Collectors.toSet());

        Map<String, CDMIPSample> lmSamples;
        Map<String, CDMIPBody> emNeurons;
        if (args.hasDataServiceURL()) {
            Client httpClient = HttpHelper.createClient();
            if (CollectionUtils.isNotEmpty(matchedLMSampleNames)) {
                lmSamples = retrieveLMSamples(httpClient, matchedLMSampleNames);
            } else {
                lmSamples = Collections.emptyMap();
            }
            if (CollectionUtils.isNotEmpty(neuronNames)) {
                emNeurons = retrieveEMNeurons(httpClient, neuronNames);
            } else {
                emNeurons = Collections.emptyMap();
            }
        } else {
            lmSamples = Collections.emptyMap();
            emNeurons = Collections.emptyMap();
        }

        return inputPPPMatches.stream()
                .map(inputPPPMatch -> {
                    PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> pppMatch = inputPPPMatch.getPPPMatch();
                    if (pppMatch.getRank() < 500) {
                        Path screenshotsPath = pppResultsFile.getParent().resolve(args.screenshotsDir);
                        lookupScreenshots(screenshotsPath, inputPPPMatch);
                    }
                    CDMIPSample lmSample = lmSamples.get(inputPPPMatch.getLmSampleName());
                    inputPPPMatch.setLmSample(lmSample);
                    CDMIPBody emBody = emNeurons.get(inputPPPMatch.getEmNeuronName());
                    inputPPPMatch.setEmBody(emBody);
                    return updatePPPMatchData(inputPPPMatch);
                })
                .collect(Collectors.toList());
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesWriter<PPPMatchEntity<M, T>>
    getPPPMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesWriter<>(getConfig());
        } else {
            return new JSONNeuronMatchesWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    AbstractNeuronEntity::getSourceRefId, // PPP results are grouped by published name
                    Comparator.comparingDouble(m -> (((PPPMatchEntity<?, ?>) m).getRank())), // ascending order by rank
                    args.getOutputDir(),
                    null // only write results per mask
            );
        }
    }

    private InputPPPMatch fillInNeuronMetadata(PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> pppMatch) {
        InputPPPMatch inputPPPMatch = new InputPPPMatch(pppMatch);
        updateEMMetadata(pppMatch.getSourceEmName(), inputPPPMatch);
        updateLMMetadata(pppMatch.getSourceLmName(), inputPPPMatch);
        return inputPPPMatch;
    }

    private void updateEMMetadata(String emFullName, InputPPPMatch inputPPPMatch) {
        Pattern emRegExPattern = Pattern.compile("([0-9]+)-([^-]*)-(.*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = emRegExPattern.matcher(emFullName);
        if (matcher.find()) {
            inputPPPMatch.setEmNeuronName(matcher.group(1));
            inputPPPMatch.setEmNeuronType(matcher.group(2));
        }
    }

    private void updateLMMetadata(String lmFullName, InputPPPMatch inputPPPMatch) {
        Pattern lmRegExPattern = Pattern.compile("(.+)_REG_UNISEX_(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = lmRegExPattern.matcher(lmFullName);
        if (matcher.find()) {
            inputPPPMatch.setLmSampleName(matcher.group(1));
            String objectiveCandidate = matcher.group(2);
            if (!StringUtils.equalsIgnoreCase(args.anatomicalArea, objectiveCandidate)) {
                inputPPPMatch.setLmObjective(objectiveCandidate);
            }
        }
    }

    private String selectADataServiceURL() {
        return args.dataServiceURLs.get(RAND.nextInt(args.dataServiceURLs.size()));
    }

    private Map<String, CDMIPSample> retrieveLMSamples(Client httpClient, Set<String> sampleNames) {
        LOG.debug("Read LM metadata for {} samples", sampleNames.size());
        return HttpHelper.retrieveDataStream(() -> httpClient.target(selectADataServiceURL())
                                .path("/data/samples")
                                .queryParam("withReducedFields", true),
                        args.authorization,
                        args.jacsReadBatchSize,
                        sampleNames,
                        new TypeReference<List<CDMIPSample>>() {
                        })
                .filter(sample -> StringUtils.isNotBlank(sample.publishingName))
                .collect(Collectors.toMap(n -> n.name, n -> n));
    }

    private Map<String, CDMIPBody> retrieveEMNeurons(Client httpClient, Set<String> neuronIds) {
        LOG.debug("Read EM metadata for {} neurons", neuronIds.size());
        return HttpHelper.retrieveDataStream(() -> httpClient.target(selectADataServiceURL())
                                .path("/emdata/dataset")
                                .path(args.emDataset)
                                .path(args.emDatasetVersion),
                        args.authorization,
                        args.jacsReadBatchSize,
                        neuronIds,
                        new TypeReference<List<CDMIPBody>>() {
                        })
                .collect(Collectors.toMap(
                        n -> n.name,
                        n -> n));
    }

    private void lookupScreenshots(Path pppScreenshotsDir, InputPPPMatch inputPPPMatch) {
        if (Files.exists(pppScreenshotsDir)) {
            try (DirectoryStream<Path> screenshotsDirStream = Files.newDirectoryStream(
                    pppScreenshotsDir,
                    inputPPPMatch.getPPPMatch().getSourceEmName() + "*" + inputPPPMatch.getPPPMatch().getSourceLmName() + "*.png")) {
                screenshotsDirStream.forEach(f -> {
                    inputPPPMatch.getPPPMatch().addSourceImageFile(f.toString());
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> updatePPPMatchData(InputPPPMatch inputPPPMatch) {
        EMNeuronEntity emNeuronEntity = new EMNeuronEntity();
        emNeuronEntity.setPublishedName(inputPPPMatch.getEmNeuronName());
        emNeuronEntity.setSourceRefId(inputPPPMatch.getEmId());

        LMNeuronEntity lmNeuronEntity = new LMNeuronEntity();
        lmNeuronEntity.setPublishedName(inputPPPMatch.getLmLineName());
        lmNeuronEntity.setSourceRefId(inputPPPMatch.getLmId());

        PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> pppMatch = inputPPPMatch.getPPPMatch();
        pppMatch.setMaskImage(emNeuronEntity);
        pppMatch.setMatchedImage(lmNeuronEntity);

        if (inputPPPMatch.getPPPMatch().hasSourceImageFiles()) {
            inputPPPMatch.getPPPMatch().getSourceImageFiles()
                    .forEach((k, fn) -> {
                        inputPPPMatch.getPPPMatch().setMatchFileData(
                                k.getFileType(),
                                FileData.fromString(buildImageRelativePath(inputPPPMatch, k.getFileType().getFileSuffix())));
                    });
        }
        return pppMatch;
    }

    private String buildImageRelativePath(InputPPPMatch inputPPPMatch, String suffix) {
        String emNeuronName = inputPPPMatch.getEmNeuronName();
        String lmNeuronName = inputPPPMatch.getLmNeuronName();
        String lmObjective = inputPPPMatch.getLmObjective();
        return emNeuronName.substring(0, 2) + '/' +
                emNeuronName + '/' +
                emNeuronName + '-' +
                lmNeuronName + "-" +
                lmObjective + "-" +
                args.alignmentSpace + '-' +
                suffix;
    }

    private void writePPPMatches(List<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> pppMatches, NeuronMatchesWriter<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> pppMatchesWriter) {
        pppMatchesWriter.write(pppMatches);
    }
}
