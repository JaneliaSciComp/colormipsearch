package org.janelia.colormipsearch.cmd.v2dataimport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api_v2.cdsearch.CDSMatches;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorMIPSearchMatchMetadata;
import org.janelia.colormipsearch.api_v2.cdsearch.ColorMIPSearchResultUtils;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronEntity;

public class JSONV2Em2LmMatchesReader implements NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> {

    private final ObjectMapper mapper;

    public JSONV2Em2LmMatchesReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Collection<String> listMatchesLocations(Collection<DataSourceParam> matchesSource) {
        /*
         * libraries attribute contains either the directories location or full path filenames.
         */
        return matchesSource.stream()
                .flatMap(arg ->  getFilesAtLocation(arg).stream())
                .collect(Collectors.toList());
    }

    private List<String> getFilesAtLocation(DataSourceParam dataSourceParam) {
        List<String> allFiles = dataSourceParam.getLibraries().stream().flatMap(l -> FSUtils.getFiles(l, 0, -1).stream())
                .skip(dataSourceParam.getOffset())
                .collect(Collectors.toList());
        if (dataSourceParam.hasSize() && dataSourceParam.getSize() < allFiles.size()) {
            return allFiles.subList(0, dataSourceParam.getSize());
        } else {
            return allFiles;
        }
    }

    private List<String> listFiles(String location, int offsetParam, int lengthParam) {
        try {
            Path pathLocation = Paths.get(location);
            if (Files.isSymbolicLink(pathLocation)) {
                return listFilesAtPath(Files.readSymbolicLink(pathLocation), offsetParam, lengthParam);
            } else {
                return listFilesAtPath(pathLocation, offsetParam, lengthParam);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> listFilesAtPath(Path pathLocation, int offsetParam, int lengthParam) {
        try {
            if (Files.isRegularFile(pathLocation)) {
                return Collections.singletonList(pathLocation.toString());
            } else if (Files.isDirectory(pathLocation)) {
                int from = Math.max(offsetParam, 0);
                List<String> filenamesList = Files.find(pathLocation, 1, (p, fa) -> fa.isRegularFile())
                        .skip(from)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                if (lengthParam > 0 && lengthParam < filenamesList.size()) {
                    return filenamesList.subList(0, lengthParam);
                } else {
                    return filenamesList;
                }
            } else {
                return Collections.emptyList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> readMatchesByMask(String alignmentSpace,
                                                                                 DataSourceParam maskDataSource,
                                                                                 DataSourceParam targetDataSource,
                                                                                 Collection<String> matchTags,
                                                                                 Collection<String> matchExcludedTags,
                                                                                 ScoresFilter matchScoresFilter,
                                                                                 List<SortCriteria> sortCriteriaList,
                                                                                 int readPageSize) {
        return maskDataSource.getMipIDs().stream()
                .flatMap(maskMipId -> CollectionUtils.isEmpty(maskDataSource.getLibraries())
                        ?  Stream.of(new File(maskMipId))
                        : maskDataSource.getLibraries().stream().map(l ->  Paths.get(l, maskMipId).toFile()))
                .map(this::readEMMatches)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> readMatchesByTarget(String alignmentSpace,
                                                                                   DataSourceParam maskDataSource,
                                                                                   DataSourceParam targetDataSource,
                                                                                   Collection<String> matchTags,
                                                                                   Collection<String> matchExcludedTags,
                                                                                   ScoresFilter matchScoresFilter,
                                                                                   List<SortCriteria> sortCriteriaList,
                                                                                   int readPageSize) {
        throw new UnsupportedOperationException("This class has very limitted support and it is only intended for import EM to LM matches based on the EM MIP ID(s)");
    }

    private List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> readEMMatches(File f) {
        CDSMatches matchesFileContent = ColorMIPSearchResultUtils.readCDSMatchesFromJSONFile(f, mapper);
        if (matchesFileContent == null || matchesFileContent.isEmpty()) {
            return Collections.emptyList();
        } else {
            return matchesFileContent.getResults().stream()
                    .map(v2CDMatch -> {
                        CDMatchEntity<EMNeuronEntity, LMNeuronEntity> cdMatch = new CDMatchEntity<>();
                        EMNeuronEntity emNeuronMetadata = new EMNeuronEntity();
                        emNeuronMetadata.setMipId(v2CDMatch.getSourceId());
                        emNeuronMetadata.setAlignmentSpace(v2CDMatch.getAlignmentSpace());
                        emNeuronMetadata.setLibraryName(v2CDMatch.getSourceLibraryName());
                        emNeuronMetadata.setPublishedName(v2CDMatch.getSourcePublishedName());

                        emNeuronMetadata.setComputeFileData(ComputeFileType.SourceColorDepthImage,
                                FileData.fromString(v2CDMatch.getSourceCdmPath()));
                        emNeuronMetadata.setComputeFileData(ComputeFileType.InputColorDepthImage,
                                FileData.fromString(getSourceImageFilename(v2CDMatch)));

                        cdMatch.setMaskImage(emNeuronMetadata);

                        LMNeuronEntity lmNeuronMetadata = new LMNeuronEntity();
                        lmNeuronMetadata.setMipId(v2CDMatch.getId());
                        lmNeuronMetadata.setAlignmentSpace(v2CDMatch.getAlignmentSpace());
                        lmNeuronMetadata.setLibraryName(v2CDMatch.getLibraryName());
                        lmNeuronMetadata.setSourceRefId(v2CDMatch.getSampleRef());
                        lmNeuronMetadata.setPublishedName(v2CDMatch.getPublishedName());
                        lmNeuronMetadata.setSlideCode(v2CDMatch.getSlideCode());

                        lmNeuronMetadata.setComputeFileData(ComputeFileType.SourceColorDepthImage,
                                FileData.fromString(v2CDMatch.getCdmPath()));
                        lmNeuronMetadata.setComputeFileData(ComputeFileType.InputColorDepthImage,
                                FileData.fromString(getTargetImageFilename(v2CDMatch)));
                        lmNeuronMetadata.setComputeFileData(ComputeFileType.GradientImage,
                                FileData.fromString(v2CDMatch.getVariant("gradient")));
                        lmNeuronMetadata.setComputeFileData(ComputeFileType.ZGapImage,
                                FileData.fromString(v2CDMatch.getVariant("zgap")));

                        cdMatch.setMatchedImage(lmNeuronMetadata);
                        cdMatch.setMirrored(v2CDMatch.isMirrored());

                        // setup the scores
                        cdMatch.setNormalizedScore((float) v2CDMatch.getNormalizedScore());
                        cdMatch.setMatchingPixels(v2CDMatch.getMatchingPixels());
                        cdMatch.setMatchingPixelsRatio((float) v2CDMatch.getMatchingRatio());
                        cdMatch.setGradientAreaGap(v2CDMatch.getGradientAreaGap());
                        cdMatch.setHighExpressionArea(v2CDMatch.getHighExpressionArea());
                        return cdMatch;
                    })
                    .collect(Collectors.toList());
        }
    }

    private String getSourceImageFilename(ColorMIPSearchMatchMetadata cdMatch) {
        if (StringUtils.isNotBlank(cdMatch.getSourceImageArchivePath())) {
            return Paths.get(cdMatch.getSourceImageArchivePath(), cdMatch.getSourceImageName()).toString();
        } else {
            return cdMatch.getSourceImageName();
        }
    }

    private String getTargetImageFilename(ColorMIPSearchMatchMetadata cdMatch) {
        if (StringUtils.isNotBlank(cdMatch.getImageArchivePath())) {
            return Paths.get(cdMatch.getImageArchivePath(), cdMatch.getImageName()).toString();
        } else {
            return cdMatch.getImageName();
        }
    }

}
