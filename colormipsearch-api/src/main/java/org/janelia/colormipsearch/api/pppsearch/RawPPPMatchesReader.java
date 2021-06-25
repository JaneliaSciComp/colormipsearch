package org.janelia.colormipsearch.api.pppsearch;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.RegExUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawPPPMatchesReader {
    private static final Logger LOG = LoggerFactory.getLogger(RawPPPMatchesReader.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<PPPMatch> readPPPMatches(String fn) {
        return readPPPMatches(new File(fn));
    }

    public List<PPPMatch> readPPPMatches(File f) {
        JsonNode jsonContent = readJSONFile(f);
        return StreamSupport.stream(Spliterators.spliterator(jsonContent.fields(), Long.MAX_VALUE, 0), false)
                .flatMap(emMatchEntry -> getLMMatches(emMatchEntry.getKey(),emMatchEntry.getValue()))
                .collect(Collectors.toList())
                ;
    }

    private Stream<PPPMatch> getLMMatches(String emFullName, JsonNode lmMatchesNode) {
        return StreamSupport.stream(Spliterators.spliterator(lmMatchesNode.fields(), Long.MAX_VALUE, 0), false)
                .map(lmMatchEntry -> {
                    PPPMatch pppMatch = new PPPMatch();
                    pppMatch.setFullEmName(emFullName);
                    pppMatch.setFullLmName(lmMatchEntry.getKey());
                    try {
                        JsonNode lmMatchContent = lmMatchEntry.getValue();
                        RawSkeletonMatches skeletonMatch = objectMapper.readValue(lmMatchContent.toString(), RawSkeletonMatches.class);
                        pppMatch.setAggregateCoverage(skeletonMatch.getAggregateCoverage());
                        pppMatch.setCoverageScore(skeletonMatch.getCoverageScore());
                        pppMatch.setMirrored(skeletonMatch.getMirrored());
                        pppMatch.setEmPPPRank(skeletonMatch.getRank());
                        pppMatch.setSkeletonMatches(getAllSkeletonMatches(skeletonMatch));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Error parsing match " +
                                emFullName + "->" + lmMatchEntry.getKey(), e);
                    }
                    return pppMatch;
                })
                ;
    }

    private List<SkeletonMatch> getAllSkeletonMatches(RawSkeletonMatches rawSkeletonMatches) throws IOException {
        List<String> bestSkeletonIds = objectMapper.readValue(rawSkeletonMatches.getBestSkeletonIds(), new TypeReference<List<String>>() {
        });
        List<Double> bestNBlastScores = objectMapper.readValue(rawSkeletonMatches.getBestNBlastScores(), new TypeReference<List<Double>>() {
        });
        List<Double> bestCoverageScores = objectMapper.readValue(rawSkeletonMatches.getBestCoveragesScores(), new TypeReference<List<Double>>() {
        });
        List<short[]> bestColors = objectMapper.readValue(rawSkeletonMatches.getBestColors(), new TypeReference<List<short[]>>() {
        });
        List<String> allSkeletonIds = objectMapper.readValue(normalizeArrayString(rawSkeletonMatches.getAllSkeletonIds()), new TypeReference<List<String>>() {
        });
        List<Double> allNBlastScores = objectMapper.readValue(normalizeArrayString(rawSkeletonMatches.getAllNBlastScores()), new TypeReference<List<Double>>() {
        });
        List<Double> allCoverageScores = objectMapper.readValue(normalizeArrayString(rawSkeletonMatches.getAllCoveragesScores()), new TypeReference<List<Double>>() {
        });
        List<short[]> allColors = objectMapper.readValue(rawSkeletonMatches.getAllColors(), new TypeReference<List<short[]>>() {
        });
        if (bestSkeletonIds.size() != bestNBlastScores.size()) {
            throw new IllegalArgumentException("The size of best skeleton ids must match the size of best nblast scores in: " + rawSkeletonMatches);
        }
        if (bestSkeletonIds.size() != bestCoverageScores.size()) {
            throw new IllegalArgumentException("The size of best skeleton ids must match the size of best coverage scores in: " + rawSkeletonMatches);
        }
        if (allSkeletonIds.size() != allNBlastScores.size()) {
            throw new IllegalArgumentException("The size of all skeleton ids must match the size of nblast scores in: " + rawSkeletonMatches);
        }
        if (allSkeletonIds.size() != allCoverageScores.size()) {
            throw new IllegalArgumentException("The size of skeleton ids must match the size of coverage scores in: " + rawSkeletonMatches);
        }
        Set<String> handledSkeletonIds = new HashSet<>();
        List<SkeletonMatch> skeletonMatches = new ArrayList<>();
        addSkeletonMatches(bestSkeletonIds, bestNBlastScores, bestCoverageScores, bestColors, skeletonMatches, handledSkeletonIds);
        addSkeletonMatches(allSkeletonIds, allNBlastScores, allCoverageScores, allColors, skeletonMatches, handledSkeletonIds);
        return skeletonMatches;
    }

    private void addSkeletonMatches(List<String> skeletonIds,
                                    List<Double> nBlastScores,
                                    List<Double> coverageScores,
                                    List<short[]> colors,
                                    List<SkeletonMatch> accumulator,
                                    Set<String> alreadyAdded) {
        for (int i = 0; i < skeletonIds.size(); i++) {
            if (!alreadyAdded.contains(skeletonIds.get(i))) {
                SkeletonMatch skeletonMatch = new SkeletonMatch();
                skeletonMatch.setId(skeletonIds.get(i));
                skeletonMatch.setNblastScore(nBlastScores.get(i));
                skeletonMatch.setCoverage(coverageScores.get(i));
                if (colors.size() == skeletonIds.size()) {
                    // only assign a color if the size matches
                    // there are cases when the colors array is empty or
                    // when the skeletonIDs contain ellipsis it is much larger and there's no easy way
                    // to match the colors with the ID
                    skeletonMatch.setColors(colors.get(i));
                }
                accumulator.add(skeletonMatch);
                alreadyAdded.add(skeletonMatch.getId());
            }
        }
    }
    private String normalizeArrayString(String s) {
        return s.replaceAll("\\[\\s+", "[")
                .replaceAll("\\.\\.\\.", "")
                .replaceAll("\\s+]", "]")
                .replaceAll("\\.]", "]")
                .replaceAll("\\s+", ", ")
                .replaceAll("\\.,", ",")
                ;
    }

    private JsonNode readJSONFile(File f) {
        try {
            LOG.info("Reading {}", f);
            return objectMapper.readTree(f);
        } catch (IOException e) {
            LOG.error("Error reading json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }
}
