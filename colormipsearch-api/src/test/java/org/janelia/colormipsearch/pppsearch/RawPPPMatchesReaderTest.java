package org.janelia.colormipsearch.pppsearch;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.janelia.colormipsearch.ppp.RawPPPMatchesReader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RawPPPMatchesReaderTest {

    private RawPPPMatchesReader rawPPPMatchesReader;

    @Before
    public void setUp() {
        rawPPPMatchesReader = new RawPPPMatchesReader(
                new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    @Test
    public void readRawPPPMatchFileWithAllSkeletonMatches() {
        String[] testFiles = new String[] {
                "src/test/resources/colormipsearch/api/pppsearch/cov_scores_1599747200-PFNp_c-RT_18U.json",
                "src/test/resources/colormipsearch/api/pppsearch/cov_scores_484130600-SMP145-RT_18U.json"
        };
        for (String testFile : testFiles) {
            List<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> pppMatchList =
                    rawPPPMatchesReader.readPPPMatches(testFile, false, true)
                            .collect(Collectors.toList());
            assertTrue(pppMatchList.size() > 0);

            String testNeuron = new File(testFile).getName()
                    .replaceAll("\\.json", "")
                    .replaceAll("cov_scores_", "");

            pppMatchList.forEach(pppMatch -> {
                assertEquals(testFile, testNeuron, pppMatch.getSourceEmName());
                assertNotNull(testFile, pppMatch.getSourceLmName());
            });
        }
    }

    @Test
    public void readRawPPPMatchFileWithBestSkeletonMatches() {
        String[] testFiles = new String[] {
                "src/test/resources/colormipsearch/api/pppsearch/cov_scores_1599747200-PFNp_c-RT_18U.json",
                "src/test/resources/colormipsearch/api/pppsearch/cov_scores_484130600-SMP145-RT_18U.json"
        };
        for (String testFile : testFiles) {
            List<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> pppMatchList =
                    rawPPPMatchesReader.readPPPMatches(testFile, true, true)
                            .collect(Collectors.toList());
            assertFalse(pppMatchList.isEmpty());

            String testNeuron = new File(testFile).getName()
                    .replaceAll("\\.json", "")
                    .replaceAll("cov_scores_", "");

            pppMatchList.forEach(pppMatch -> {
                assertEquals(testFile, testNeuron, pppMatch.getSourceEmName());
                assertNotNull(testFile, pppMatch.getSourceLmName());
            });
        }
    }
}
