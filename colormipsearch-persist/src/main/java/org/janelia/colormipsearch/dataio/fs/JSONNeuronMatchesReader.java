package org.janelia.colormipsearch.dataio.fs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;

public class JSONNeuronMatchesReader<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> implements NeuronMatchesReader<R> {
    private final ObjectMapper mapper;

    public JSONNeuronMatchesReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> listMatchesLocations(Collection<DataSourceParam> matchesSource) {
        /*
         * For JSON file reader the libraryName attribute contains the directory location.
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

    @SuppressWarnings("unchecked")
    @Override
    public List<R> readMatchesByMask(String alignmentSpace,
                                     Collection<String> maskLibraries,
                                     Collection<String> maskPublishedNames,
                                     Collection<String> maskMipIds,
                                     Collection<String> maskDatasets,
                                     Collection<String> maskTags,
                                     Collection<String> maskExcludedTags,
                                     Collection<String> targetLibraries,
                                     Collection<String> targetPublishedNames,
                                     Collection<String> targetMipIds,
                                     Collection<String> targetDatasets,
                                     Collection<String> targetTags,
                                     Collection<String> targetExcludedTags,
                                     Collection<String> matchTags,
                                     Collection<String> matchExcludedTags,
                                     ScoresFilter matchScoresFilter,
                                     List<SortCriteria> sortCriteriaList) {
        return (List<R>) maskMipIds.stream()
                .flatMap(maskMipId -> CollectionUtils.isEmpty(maskLibraries)
                        ?  Stream.of(new File(maskMipId))
                        : maskLibraries.stream().map(l ->  Paths.get(l, maskMipId).toFile()))
                .map(this::readMatchesResults)
                .flatMap(resultMatches -> MatchEntitiesGrouping.expandResultsByMask(resultMatches).stream())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<R> readMatchesByTarget(String alignmentSpace,
                                       Collection<String> maskLibraries,
                                       Collection<String> maskPublishedNames,
                                       Collection<String> maskMipIds,
                                       Collection<String> maskDatasets,
                                       Collection<String> maskTags,
                                       Collection<String> maskExcludedTags,
                                       Collection<String> targetLibraries,
                                       Collection<String> targetPublishedNames,
                                       Collection<String> targetMipIds,
                                       Collection<String> targetDatasets,
                                       Collection<String> targetTags,
                                       Collection<String> targetExcludedTags,
                                       Collection<String> matchTags,
                                       Collection<String> matchExcludedTags,
                                       ScoresFilter matchScoresFilter,
                                       List<SortCriteria> sortCriteriaList) {
        return (List<R>) targetMipIds.stream()
                .flatMap(targetMipId -> CollectionUtils.isEmpty(targetLibraries)
                        ?  Stream.of(new File(targetMipId))
                        : targetLibraries.stream().map(l ->  Paths.get(l, targetMipId).toFile()))
                .map(this::readMatchesResults)
                .flatMap(resultMatches -> MatchEntitiesGrouping.expandResultsByTarget(resultMatches).stream())
                .collect(Collectors.toList());
    }

    private <M1 extends AbstractNeuronEntity, T1 extends AbstractNeuronEntity, R1 extends AbstractMatchEntity<M1, T1>> GroupedMatchedEntities<M1, T1, R1> readMatchesResults(File f) {
        try {
            return mapper.readValue(f, new TypeReference<GroupedMatchedEntities<M1, T1, R1>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading CDSMatches from JSON file:" + f, e);
        }
    }

}
