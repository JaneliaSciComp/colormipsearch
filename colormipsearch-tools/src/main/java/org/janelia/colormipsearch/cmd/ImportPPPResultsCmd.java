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
import org.janelia.colormipsearch.cmd.io.JSONPPPResultsWriter;
import org.janelia.colormipsearch.cmd.io.ResultMatchesWriter;
import org.janelia.colormipsearch.cmd.jacsdata.CDMIPBody;
import org.janelia.colormipsearch.cmd.jacsdata.CDMIPSample;
import org.janelia.colormipsearch.model.EMNeuronMetadata;
import org.janelia.colormipsearch.model.Gender;
import org.janelia.colormipsearch.model.LMNeuronMetadata;
import org.janelia.colormipsearch.model.PPPMatch;
import org.janelia.colormipsearch.ppp.RawPPPMatchesReader;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ImportPPPResultsCmd extends AbstractCmd {
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

    public ImportPPPResultsCmd(String commandName, CommonArgs commonArgs) {
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
                filesToProcess.parallel(),
                args.processingPartitionSize,
                this::processPPPFiles);
        LOG.info("Processed all files in {}s", (System.currentTimeMillis() - startTime) / 1000.);
    }

    private void processPPPFiles(List<Path> listOfPPPResults) {
        long start = System.currentTimeMillis();
        listOfPPPResults.stream()
                .peek(fp -> MDC.put("PPPFile", fp.getFileName().toString()))
                .map(this::importPPPRResultsFromFile)
                .forEach(pppMatches -> {
                    ResultMatchesWriter<EMNeuronMetadata, LMNeuronMetadata, PPPMatch<EMNeuronMetadata, LMNeuronMetadata>> pppResultsWriter = new JSONPPPResultsWriter<>(
                            args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                            args.getOutputDir());
                    pppResultsWriter.write(pppMatches);
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
    private List<PPPMatch<EMNeuronMetadata, LMNeuronMetadata>> importPPPRResultsFromFile(Path pppResultsFile) {
        List<PPPMatch<EMNeuronMetadata, LMNeuronMetadata>> neuronMatches = rawPPPMatchesReader.readPPPMatches(
                        pppResultsFile.toString(), args.onlyBestSkeletonMatches)
                .peek(this::fillInNeuronMetadata)
                .collect(Collectors.toList());
        Set<String> matchedLMSampleNames = neuronMatches.stream()
                .map(pppMatch -> pppMatch.getMatchedImage().getSampleName())
                .collect(Collectors.toSet());
        Set<String> neuronNames = neuronMatches.stream()
                .map(pppMatch -> pppMatch.getMaskImage().getPublishedName())
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

        neuronMatches.forEach(pppMatch -> {
            if (pppMatch.getRank() < 500) {
                Path screenshotsPath = pppResultsFile.getParent().resolve(args.screenshotsDir);
                lookupScreenshots(screenshotsPath, pppMatch);
            }
            LMNeuronMetadata lmNeuron = pppMatch.getMatchedImage();
            CDMIPSample lmSample = lmSamples.get(lmNeuron.getSampleName());
            if (lmSample != null) {
                lmNeuron.setDatasetName(lmSample.releaseLabel); // for now set this to the releaseLabel but this is not quite right
                lmNeuron.setSampleRef("Sample#" + lmSample.id);
                lmNeuron.setPublishedName(lmSample.publishingName);
                lmNeuron.setSlideCode(lmSample.slideCode);
                lmNeuron.setGender(Gender.fromVal(lmSample.gender));
                lmNeuron.setMountingProtocol(lmSample.mountingProtocol);
                if (StringUtils.isBlank(lmNeuron.getObjective())) {
                    if (CollectionUtils.size(lmSample.publishedObjectives) == 1) {
                        lmNeuron.setObjective(lmSample.publishedObjectives.get(0));
                    } else {
                        throw new IllegalArgumentException("Too many published objectives for sample " + lmSample +
                                ". Cannot decide which objective to select for " + pppMatch);
                    }
                }
            }
            EMNeuronMetadata emNeuron = pppMatch.getMaskImage();
            CDMIPBody emBody = emNeurons.get(emNeuron.getPublishedName());
            if (emBody != null) {
                emNeuron.setBodyRef("EMBody#" + emBody.id);
                emNeuron.setDatasetName(emBody.datasetIdentifier); // this should be set to the library id which differs slightly from the EM dataset
                emNeuron.setNeuronType(emBody.neuronType);
                emNeuron.setNeuronInstance(emBody.neuronInstance);
                emNeuron.setState(emBody.status);
            }
        });
        return neuronMatches;
    }

    private void fillInNeuronMetadata(PPPMatch<EMNeuronMetadata, LMNeuronMetadata> pppMatch) {
        pppMatch.setMaskImage(getEMMetadata(pppMatch.getSourceEmName()));
        pppMatch.setMatchedImage(getLMMetadata(pppMatch.getSourceLmName()));
    }

    private EMNeuronMetadata getEMMetadata(String emFullName) {
        EMNeuronMetadata emNeuron = new EMNeuronMetadata();
        emNeuron.setAlignmentSpace(args.alignmentSpace);
        Pattern emRegExPattern = Pattern.compile("([0-9]+)-([^-]*)-(.*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = emRegExPattern.matcher(emFullName);
        if (matcher.find()) {
            emNeuron.setPublishedName(matcher.group(1)); // neuron name
            emNeuron.setNeuronType(matcher.group(2));
        }
        return emNeuron;
    }

    private LMNeuronMetadata getLMMetadata(String lmFullName) {
        LMNeuronMetadata lmNeuron = new LMNeuronMetadata();
        lmNeuron.setAlignmentSpace(args.alignmentSpace);
        lmNeuron.setAnatomicalArea(args.anatomicalArea);
        Pattern lmRegExPattern = Pattern.compile("(.+)_REG_UNISEX_(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = lmRegExPattern.matcher(lmFullName);
        if (matcher.find()) {
            lmNeuron.setSampleName(matcher.group(1));
            String objectiveCandidate = matcher.group(2);
            if (!StringUtils.equalsIgnoreCase(args.anatomicalArea, objectiveCandidate)) {
                lmNeuron.setObjective(objectiveCandidate);
            }
        }
        return lmNeuron;
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

    private void lookupScreenshots(Path pppScreenshotsDir, PPPMatch<?, ?> pppMatch) {
        if (Files.exists(pppScreenshotsDir)) {
            try (DirectoryStream<Path> screenshotsDirStream = Files.newDirectoryStream(pppScreenshotsDir, pppMatch.getSourceEmName() + "*" + pppMatch.getSourceLmName() + "*.png")) {
                screenshotsDirStream.forEach(f -> {
                    pppMatch.addSourceImageFile(f.toString());
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            pppMatch.updateMatchFiles();
        }
    }

}