package org.janelia.colormipsearch.tools;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

    @Test
    public void pickBestResultsWithAllSubResults() {
        List<ColorMIPSearchMatchMetadata> testData = createTestData();
        for (int i = 1; i <= 3; i++) {
            List<ColorMIPSearchMatchMetadata> selectedEntries = Utils.pickBestMatches(
                    testData,
                    csr -> csr.getPublishedName(),
                    ColorMIPSearchMatchMetadata::getMatchingPixels,
                    i,
                    -1).stream()
                    .flatMap(se -> se.getEntry().stream())
                    .collect(Collectors.toList());
            assertEquals(3 * i, selectedEntries.size());
        }
    }

    @Test
    public void pickBestResultsWithSubResults() {
        List<ColorMIPSearchMatchMetadata> testData = createTestData();
        for (int i = 1; i <= 3; i++) {
            for (int si = 1; si <= 3; si++) {
                int topSubResults = si;
                List<ColorMIPSearchMatchMetadata> selectedEntries = Utils.pickBestMatches(
                        testData,
                        ColorMIPSearchMatchMetadata::getPublishedName,
                        ColorMIPSearchMatchMetadata::getMatchingPixels,
                        i,
                        -1).stream()
                        .flatMap(se -> Utils.pickBestMatches(
                                se.getEntry(),
                                AbstractMetadata::getSlideCode, // pick best results by sample (identified by slide code)
                                ColorMIPSearchMatchMetadata::getMatchingPixels,
                                topSubResults,
                                -1).stream())
                        .flatMap(se -> se.getEntry().stream())
                        .collect(Collectors.toList());
                assertEquals(i * si, selectedEntries.size());
            }
        }
    }

    @Test
    public void pickBestResultsWithLimitedSubResults() {
        List<ColorMIPSearchMatchMetadata> testData = createTestData();
        for (int i = 1; i <= 3; i++) {
            for (int si = 1; si <= 3; si++) {
                int topSubResults = si;
                List<ColorMIPSearchMatchMetadata> selectedEntries = Utils.pickBestMatches(
                        testData,
                        ColorMIPSearchMatchMetadata::getPublishedName,
                        ColorMIPSearchMatchMetadata::getMatchingPixels,
                        i,
                        1).stream()
                        .flatMap(se -> Utils.pickBestMatches(
                                se.getEntry(),
                                AbstractMetadata::getSlideCode, // pick best results by sample (identified by slide code)
                                ColorMIPSearchMatchMetadata::getMatchingPixels,
                                topSubResults,
                                -1).stream())
                        .flatMap(se -> se.getEntry().stream())
                        .collect(Collectors.toList());
                assertEquals(i, selectedEntries.size());
            }
        }
    }

    @Test
    public void eliminateDuplicateResults() {
        List<ColorMIPSearchMatchMetadata> testData = ImmutableList.<ColorMIPSearchMatchMetadata>builder()
                .add(createCSRWithMatchedIds("1", "10", "i1.1", "i10", 10))
                .add(createCSRWithMatchedIds("1", "10", "i1.2", "i10", 10))
                .add(createCSRWithMatchedIds("1", "20", "i1.1", "i20", 10))
                .add(createCSRWithMatchedIds("1", "30", "i1.1", "i30", 10))
                .add(createCSRWithMatchedIds("1", "30", "i1.2", "i30", 10))
                .build();
        List<ColorMIPSearchMatchMetadata> resultsWithNoDuplicates = Utils.pickBestMatches(
                testData,
                ColorMIPSearchMatchMetadata::getId,
                ColorMIPSearchMatchMetadata::getMatchingPixels,
                -1,
                1)
                .stream()
                .flatMap(se -> se.getEntry().stream()).collect(Collectors.toList());
        assertEquals(3, resultsWithNoDuplicates.size());
    }

    private List<ColorMIPSearchMatchMetadata> createTestData() {
        return ImmutableList.<ColorMIPSearchMatchMetadata>builder()
                .add(createCSR("l1", "s1.1", 1))
                .add(createCSR("l1", "s1.2", 2))
                .add(createCSR("l1", "s1.3", 3))
                .add(createCSR("l2", "s2.1", 10))
                .add(createCSR("l2", "s2.2", 20))
                .add(createCSR("l2", "s2.3", 30))
                .add(createCSR("l3", "s3.1", 100))
                .add(createCSR("l3", "s3.2", 200))
                .add(createCSR("l3", "s3.3", 300))
                .build();
    }

    ColorMIPSearchMatchMetadata createCSR(String line, String sample, int matchingPixels) {
        ColorMIPSearchMatchMetadata csr = new ColorMIPSearchMatchMetadata();
        csr.setPublishedName(line);
        csr.setSlideCode(sample);
        csr.setMatchingPixels(matchingPixels);
        return csr;
    }

    ColorMIPSearchMatchMetadata createCSRWithMatchedIds(String id, String matchedId, String imageName, String matchedImageName, int matchingPixels) {
        ColorMIPSearchMatchMetadata csr = new ColorMIPSearchMatchMetadata();
        csr.setSourceId(id);
        csr.setSourceImageName(imageName);
        csr.setId(matchedId);
        csr.setImageName(matchedImageName);
        csr.setMatchingPixels(matchingPixels);
        return csr;
    }

}
