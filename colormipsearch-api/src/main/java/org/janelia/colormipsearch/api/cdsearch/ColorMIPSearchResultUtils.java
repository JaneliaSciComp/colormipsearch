package org.janelia.colormipsearch.api.cdsearch;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api.Results;
import org.janelia.colormipsearch.api.ScoredEntry;
import org.janelia.colormipsearch.api.Utils;
import org.janelia.colormipsearch.api.cdmips.MIPIdentifier;
import org.janelia.colormipsearch.api.cdmips.MIPMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColorMIPSearchResultUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ColorMIPSearchResultUtils.class);

    /**
     * Map results using the provided mapping and group them by source published name.
     * @param results
     * @param resultMapper basically specify whether the results grouping is done by mask or by library.
     * @return
     */
    public static List<CDSMatches> groupResults(List<ColorMIPSearchResult> results,
                                                Function<ColorMIPSearchResult, ColorMIPSearchMatchMetadata> resultMapper) {
        return results.stream()
                .map(resultMapper)
                .collect(Collectors.groupingBy(
                        csr -> new MIPIdentifier(
                                csr.getSourceId(),
                                csr.getSourcePublishedName(),
                                csr.getSourceLibraryName(),
                                csr.getSourceImageURL()
                        ),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                l -> l.stream()
                                        .peek(csr -> csr.setSourceImageURL(null))
                                        .sorted(Comparator.comparing(ColorMIPSearchMatchMetadata::getMatchingPixels).reversed())
                                        .collect(Collectors.toList()))))
                .entrySet().stream().map(e -> new CDSMatches(
                        e.getKey().getId(),
                        e.getKey().getPublishedName(),
                        e.getKey().getLibraryName(),
                        e.getKey().getImageURL(),
                        e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Read CDS matches from the specified JSON formatted file.
     *
     * @param f
     * @param mapper
     * @return
     */
    public static CDSMatches readCDSMatchesFromJSONFile(File f, ObjectMapper mapper) {
        try {
            LOG.debug("Reading {}", f);
            return mapper.readValue(f, CDSMatches.class);
        } catch (IOException e) {
            LOG.error("Error reading CDS results from json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

    static Results<List<ColorMIPSearchMatchMetadata>> readCDSResultsFromJSONFile(File f, ObjectMapper mapper) {
        try {
            LOG.debug("Reading {}", f);
            return mapper.readValue(f, new TypeReference<Results<List<ColorMIPSearchMatchMetadata>>>() {
            });
        } catch (IOException e) {
            LOG.error("Error reading CDS results from json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

    public static Map<MIPMetadata, List<ColorMIPSearchMatchMetadata>> selectCDSResultForGradientScoreCalculation(List<ColorMIPSearchMatchMetadata> cdsResults,
                                                                                                                 int numberOfBestLinesToSelect,
                                                                                                                 int numberOfBestSamplesToSelectPerLine,
                                                                                                                 int numberOfBestMatchesToSelectPerSample) {
        return cdsResults.stream()
                .peek(csr -> csr.setGradientAreaGap(-1))
                .collect(Collectors.groupingBy(csr -> {
                    MIPMetadata mip = new MIPMetadata();
                    mip.setId(csr.getSourceId());
                    mip.setImageArchivePath(csr.getSourceImageArchivePath());
                    mip.setImageName(csr.getSourceImageName());
                    mip.setImageType(csr.getSourceImageType());
                    return mip;
                }, Collectors.collectingAndThen(
                        Collectors.toList(),
                        resultsForAnId -> {
                            List<ColorMIPSearchMatchMetadata> bestMatches = pickBestPublishedNameAndSampleMatches(
                                    resultsForAnId,
                                    numberOfBestLinesToSelect,
                                    numberOfBestSamplesToSelectPerLine,
                                    numberOfBestMatchesToSelectPerSample);
                            LOG.info("Selected {} best matches out of {}", bestMatches.size(), resultsForAnId.size());
                            return bestMatches;
                        })));
    }

    private static List<ColorMIPSearchMatchMetadata> pickBestPublishedNameAndSampleMatches(List<ColorMIPSearchMatchMetadata> cdsResults,
                                                                                           int numberOfBestPublishedNamesToSelect,
                                                                                           int numberOfBestSamplesToSelectPerPublishedName,
                                                                                           int numberOfBestMatchesToSelectPerSample) {
        List<ScoredEntry<List<ColorMIPSearchMatchMetadata>>> topResultsByPublishedName = Utils.pickBestMatches(
                cdsResults,
                csr -> StringUtils.defaultIfBlank(csr.getPublishedName(), extractPublishingNameCandidateFromImageName(csr.getImageName())), // pick best results by line
                ColorMIPSearchMatchMetadata::getMatchingPixels,
                numberOfBestPublishedNamesToSelect,
                -1);

        return topResultsByPublishedName.stream()
                .flatMap(se -> Utils.pickBestMatches(
                        se.getEntry(),
                        csr -> csr.getSlideCode(), // pick best results by sample (identified by slide code)
                        ColorMIPSearchMatchMetadata::getMatchingPixels,
                        numberOfBestSamplesToSelectPerPublishedName,
                        numberOfBestMatchesToSelectPerSample)
                        .stream())
                .flatMap(se -> se.getEntry().stream())
                .collect(Collectors.toList());
    }

    private static String extractPublishingNameCandidateFromImageName(String imageName) {
        String fn = RegExUtils.replacePattern(new File(imageName).getName(), "\\.\\D*$", "");
        int sepIndex = fn.indexOf('_');
        return sepIndex > 0 ? fn.substring(0, sepIndex) : fn;
    }

    /**
     * Sort results by tge normalized score if it is available. If not use matching pixels attribute.
     * @param cdsResults
     */
    public static void sortCDSResults(List<ColorMIPSearchMatchMetadata> cdsResults) {
        Comparator<ColorMIPSearchMatchMetadata> csrComp = (csr1, csr2) -> {
            if (csr1.getNormalizedScore() != null && csr2.getNormalizedScore() != null) {
                return Comparator.comparingDouble(ColorMIPSearchMatchMetadata::getNormalizedScore)
                        .compare(csr1, csr2)
                        ;
            } else if (csr1.getNormalizedScore() == null && csr2.getNormalizedScore() == null) {
                return Comparator.comparingInt(ColorMIPSearchMatchMetadata::getMatchingPixels)
                        .compare(csr1, csr2)
                        ;
            } else if (csr1.getNormalizedScore() == null) {
                // null gap scores should be at the beginning
                return -1;
            } else {
                return 1;
            }
        };
        cdsResults.sort(csrComp.reversed());
    }

    public static void writeCDSMatchesToJSONFile(CDSMatches cdsMatches, File f, ObjectWriter objectWriter) {
        try {
            if (CollectionUtils.isNotEmpty(cdsMatches.results)) {
                if (f == null) {
                    objectWriter.writeValue(System.out, cdsMatches);
                } else {
                    LOG.info("Writing {}", f);
                    objectWriter.writeValue(f, cdsMatches);
                }
            }
        } catch (IOException e) {
            LOG.error("Error writing CDS results to json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

}