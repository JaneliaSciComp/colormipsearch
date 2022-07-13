package org.janelia.colormipsearch.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.janelia.colormipsearch.io.CDMatchesReader;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDMatch;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileType;
import org.janelia.colormipsearch.model.MatchComputeFileType;
import org.janelia.colormipsearch.results.ResultMatches;

public class JSONFileCDMatchesReader<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> implements CDMatchesReader<M, T> {
    private final List<String> cdMatchResultFiles;
    private final ObjectMapper mapper;

    public JSONFileCDMatchesReader(List<String> cdMatchResultFiles, ObjectMapper mapper) {
        this.cdMatchResultFiles = cdMatchResultFiles;
        this.mapper = mapper;
    }

    @Override
    public List<String> listCDMatchesLocations() {
        return cdMatchResultFiles;
    }

    @Override
    public List<CDMatch<M, T>> readCDMatches(String filename) {
        ResultMatches<M, T, CDMatch<M, T>> cdsResults = readCDSResults(new File(filename));
        return convertCDSResultsToListOfMatches(cdsResults);
    }

    private ResultMatches<M, T, CDMatch<M, T>> readCDSResults(File f) {
        try {
            return mapper.readValue(f, new TypeReference<ResultMatches<M, T, CDMatch<M, T>>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading CDSMatches from JSON file:" + f, e);
        }
    }

    private List<CDMatch<M, T>> convertCDSResultsToListOfMatches(ResultMatches<M, T, CDMatch<M, T>> cdsResults) {
        return cdsResults.getItems().stream()
                .map(persistedMatch -> {
                    // extra assignment needed for Java type check
                    CDMatch<M, T> match = persistedMatch.duplicate((src, dest) -> {
                        M maskImage = cdsResults.getKey().duplicate();
                        T targetImage = persistedMatch.getMatchedImage();
                        maskImage.setComputeFileData(ComputeFileType.InputColorDepthImage,
                                persistedMatch.getMatchComputeFileData(MatchComputeFileType.MaskColorDepthImage));
                        maskImage.setComputeFileData(ComputeFileType.GradientImage,
                                persistedMatch.getMatchComputeFileData(MatchComputeFileType.MaskGradientImage));
                        maskImage.setComputeFileData(ComputeFileType.ZGapImage,
                                persistedMatch.getMatchComputeFileData(MatchComputeFileType.MaskZGapImage));
                        maskImage.setNeuronFileData(FileType.ColorDepthMipInput,
                                persistedMatch.getMatchFileData(FileType.ColorDepthMipInput));
                        dest.setMaskImage(maskImage);
                        dest.setMatchedImage(targetImage);
                        // no reason to keep these around
                        dest.resetMatchComputeFiles();
                        dest.resetMatchFiles();
                    });
                    return match;
                })
                .collect(Collectors.toList());
    }
}