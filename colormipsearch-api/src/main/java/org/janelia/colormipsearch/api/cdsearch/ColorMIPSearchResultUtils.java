package org.janelia.colormipsearch.api.cdsearch;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.api.Results;
import org.janelia.colormipsearch.api.cdmips.MIPIdentifier;
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
    public static List<CDSMatches> groupResults(List<ColorMIPSearchResult> results, Function<ColorMIPSearchResult, ColorMIPSearchMatchMetadata> resultMapper) {
        return results.stream()
                .map(resultMapper)
                .collect(Collectors.groupingBy(
                        csr -> new MIPIdentifier(
                                csr.getSourceId(),
                                csr.getSourcePublishedName(),
                                csr.getSourceLibraryName()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                l -> {
                                    l.sort(Comparator.comparing(ColorMIPSearchMatchMetadata::getMatchingPixels).reversed());
                                    return l;
                                })))
                .entrySet().stream().map(e -> new CDSMatches(
                        e.getKey().getId(),
                        e.getKey().getPublishedName(),
                        e.getKey().getLibraryName(),
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
