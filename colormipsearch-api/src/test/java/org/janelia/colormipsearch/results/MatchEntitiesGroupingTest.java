package org.janelia.colormipsearch.results;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.janelia.colormipsearch.model.GroupedMatchedEntities;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.TestEMNeuronEntity;
import org.janelia.colormipsearch.model.TestLMNeuronEntity;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchEntitiesGroupingTest {
    private static final String TESTCDSMATCHES_FILE= "src/test/resources/colormipsearch/results/testcdsmatches.json";

    @Test
    public void groupByMask() {
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> testMatches = readTestMatches();
        checkGroupedResults(
                MatchEntitiesGrouping.groupByMaskFields(
                        testMatches,
                        Collections.singletonList(
                                AbstractNeuronEntity::getMipId
                        ),
                        m -> m.getMatchedImage() != null,
                        Comparator.comparingDouble(aCDSMatch -> Math.abs(aCDSMatch.getNormalizedScore()))),
                TestEMNeuronEntity.class,
                TestLMNeuronEntity.class
        );
    }

    @Test
    public void groupByMatched() {
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> testMatches = readTestMatches();
        checkGroupedResults(
                MatchEntitiesGrouping.groupByTargetFields(
                        testMatches,
                        Collections.singletonList(
                                AbstractNeuronEntity::getMipId
                        ),
                        m -> m.getMatchedImage() != null,
                        Comparator.comparingDouble(aCDSMatch -> Math.abs(aCDSMatch.getNormalizedScore()))),
                TestLMNeuronEntity.class,
                TestEMNeuronEntity.class
        );
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void checkGroupedResults(
            List<GroupedMatchedEntities<M, T, CDMatchEntity<M, T>>> cdsResultsList,
            Class<M> expectedMaskClass,
            Class<T> expectedTargetClass) {
        assertTrue(cdsResultsList.size() > 0);
        for (GroupedMatchedEntities<M, T, CDMatchEntity<M, T>> cdsResults : cdsResultsList) {
            assertEquals(expectedMaskClass, cdsResults.getKey().getClass());
            for (CDMatchEntity<M, T> CDMatch : cdsResults.getItems()) {
                assertNull(CDMatch.getMaskImage());
                assertNotNull(CDMatch.getMatchedImage());
                assertEquals(expectedTargetClass, CDMatch.getMatchedImage().getClass());
                assertTrue(CDMatch.getMatchComputeFiles().size() > 0);
            }
        }
    }

    @Test
    public void expandResultsGroupedByMask() {
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> testMatches = readTestMatches();
        List<GroupedMatchedEntities<TestEMNeuronEntity, TestLMNeuronEntity, CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>>> groupedResults = MatchEntitiesGrouping.groupByMaskFields(
                testMatches,
                Collections.singletonList(
                        AbstractNeuronEntity::getMipId
                ),
                m -> m.getMatchedImage() != null,
                Comparator.comparingDouble(aCDSMatch -> Math.abs(aCDSMatch.getNormalizedScore())));
        Comparator<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> ordering =
                Comparator.comparing(m -> m.getMaskImage().getMipId() + m.getMatchedImage().getMipId() + m.getMatchingPixels());
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> expectedResults =
                testMatches.stream()
                        .peek(m -> { m.resetMatchComputeFiles(); })
                        .sorted(ordering)
                        .collect(Collectors.toList());
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> expandedResults =
                groupedResults.stream()
                        .map(MatchEntitiesGrouping::expandResultsByMask)
                        .flatMap(Collection::stream)
                        .sorted(ordering)
                        .collect(Collectors.toList());
        assertArrayEquals(expectedResults.toArray(), expandedResults.toArray());
    }

    @Test
    public void expandResultsGroupedByTarget() {
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> testMatches = readTestMatches();
        List<GroupedMatchedEntities<TestLMNeuronEntity, TestEMNeuronEntity, CDMatchEntity<TestLMNeuronEntity, TestEMNeuronEntity>>> groupedResults = MatchEntitiesGrouping.groupByTargetFields(
                testMatches,
                Collections.singletonList(
                        AbstractNeuronEntity::getMipId
                ),
                m -> m.getMatchedImage() != null,
                Comparator.comparingDouble(aCDSMatch -> Math.abs(aCDSMatch.getNormalizedScore())));
        Comparator<AbstractMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> ordering =
                Comparator.comparing(m -> m.getMaskImage().getMipId() + m.getMatchedImage().getMipId() + ((CDMatchEntity<?,?>) m).getMatchingPixels());
        List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> expectedResults =
                testMatches.stream()
                        .peek(m -> { m.resetMatchComputeFiles(); })
                        .sorted(ordering)
                        .collect(Collectors.toList());
        List<AbstractMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> expandedResults =
                groupedResults.stream()
                        .map(MatchEntitiesGrouping::expandResultsByTarget)
                        .flatMap(Collection::stream)
                        .sorted(ordering)
                        .collect(Collectors.toList());
        assertArrayEquals(expectedResults.toArray(), expandedResults.toArray());
    }

    private List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>> readTestMatches() {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(new File(TESTCDSMATCHES_FILE), new TypeReference<List<CDMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity>>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
