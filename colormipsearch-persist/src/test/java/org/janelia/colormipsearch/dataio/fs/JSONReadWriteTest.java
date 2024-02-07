package org.janelia.colormipsearch.dataio.fs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JSONReadWriteTest {
    private static final String TESTCDSMATCHES_FILE= "src/test/resources/cdsmatches/testcdsmatches.json";

    private static Path testDataDir;
    private static Path em2lmDir;
    private static Path lm2emDir;

    private ObjectMapper mapper;
    private JSONNeuronMatchesWriter<EMNeuronEntity, LMNeuronEntity, CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> em2lmJsonWriter;
    private JSONNeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> em2lmMatchesReader;

    @BeforeClass
    public static void createTestDataDir() throws IOException {
        testDataDir = Files.createTempDirectory("cdsjsontest");
        em2lmDir = testDataDir.resolve("em2lm");
        lm2emDir = testDataDir.resolve("lm2em");
    }

    @AfterClass
    public static void removeTestDataDir() throws IOException {
        FileUtils.deleteDirectory(testDataDir.toFile());
    }

    @Before
    public void setUp() {
        mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        em2lmJsonWriter = new JSONNeuronMatchesWriter<>(mapper.writerWithDefaultPrettyPrinter(),
                AbstractNeuronEntity::getMipId, // group results by neuron MIP ID
                Comparator.comparingDouble(m -> -(((CDMatchEntity<?,?>) m).getMatchingPixels())), // descending order by matching pixels
                em2lmDir,
                lm2emDir
        );
        em2lmMatchesReader = new JSONNeuronMatchesReader<>(
                mapper
        );
    }

    @Test
    public void jsonCDSMatchSerialization() {
        Path testJsonOutput = testDataDir.resolve("testcdsout.json");
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> CDMatches = readTestMatches(new File(TESTCDSMATCHES_FILE));
        JsonOutputHelper.writeToJSONFile(CDMatches, testJsonOutput, mapper.writerWithDefaultPrettyPrinter());
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> readCDMatches = readTestMatches(testJsonOutput.toFile());
        assertNotNull(readCDMatches);
        assertEquals(CDMatches, readCDMatches);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void readWriteCDSResults() {
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatches = readTestMatches(new File(TESTCDSMATCHES_FILE));
        em2lmJsonWriter.write(cdMatches);
        checkResultFiles(cdMatches, em2lmDir, m -> m.getMaskImage().getMipId());
        checkResultFiles(cdMatches, lm2emDir, m -> m.getMatchedImage().getMipId());

        FSUtils.getFiles(em2lmDir.toString(), 0, -1)
                .forEach(f -> {
                    List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>  matchesFromFile =
                            em2lmMatchesReader.readMatchesByMask(
                                    /* alignmentSpace */null,
                                    /* maskLibraries */null,
                                    /* maskPublishedNames */null,
                                    Collections.singletonList(f),
                                    /* maskDatasets */null,
                                    /* maskTags */null,
                                    /* maskExcludedTags */null,
                                    /* targetLibraries */null,
                                    /* targetPublishedNames */null,
                                    /* targetMIPIDs */null,
                                    /* targetDatasets*/null,
                                    /* targetTags */null,
                                    /* targetExcludedTags */null,
                                    /* matchTags */null,
                                    /* matchExcludedTags */null,
                                    /* matchScoresFilter */null,
                                    /* sortCriteria */null);
                    assertTrue(matchesFromFile.size() > 0);
                    String mId = FilenameUtils.getBaseName(f);
                    List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testMatchesWithSameMask = cdMatches.stream()
                            .filter(cdsMatch -> cdsMatch.getMaskImage().getMipId().equals(mId))
                            .peek(AbstractMatchEntity::resetMatchComputeFiles)
                            .sorted(Comparator.comparingDouble(m -> -m.getMatchingPixels()))
                            .collect(Collectors.toList());
                    assertEquals("Results did not match for " + mId,
                            testMatchesWithSameMask, matchesFromFile);
                });
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void checkResultFiles(List<CDMatchEntity<M, T>> matches,
                                                                                                   Path resultsDir,
                                                                                                   Function<CDMatchEntity<?, ?>, String> fnSelector) {
        matches.stream()
                .map(fnSelector)
                .forEach(fname -> {
                    assertTrue(Files.exists(resultsDir.resolve(fname + ".json")));
                });
    }

    private List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> readTestMatches(File f) {
        try {
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> CDMatches =
                    mapper.readValue(f, new TypeReference<List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>>() {});
            CDMatches.sort(Comparator.comparingDouble(m -> -m.getMatchingPixels()));
            return CDMatches;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
