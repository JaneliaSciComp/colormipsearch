package org.janelia.colormipsearch.dataio.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class DBNeuronMatchesReader<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> implements NeuronMatchesReader<R> {

    private final static int PAGE_SIZE = 10000;

    private final NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao;
    private final NeuronMatchesDao<R> neuronMatchesDao;
    private final String neuronLocationAttributeName;

    public DBNeuronMatchesReader(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao,
                                 NeuronMatchesDao<R> neuronMatchesDao,
                                 String neuronLocationAttributeName) {
        this.neuronMetadataDao = neuronMetadataDao;
        this.neuronMatchesDao = neuronMatchesDao;
        this.neuronLocationAttributeName = neuronLocationAttributeName;
    }

    @Override
    public List<String> listMatchesLocations(Collection<DataSourceParam> matchesSource) {
        return matchesSource.stream()
                        .flatMap(cdMatchInput -> neuronMetadataDao.findDistinctNeuronAttributeValues(
                                Collections.singletonList(neuronLocationAttributeName),
                                new NeuronSelector()
                                        .setAlignmentSpace(cdMatchInput.getAlignmentSpace())
                                        .addLibraries(cdMatchInput.getLibraries())
                                        .addMipIDs(cdMatchInput.getMipIDs())
                                        .addNames(cdMatchInput.getNames())
                                        .addTags(cdMatchInput.getTags())
                                        .addDatasetLabels(cdMatchInput.getDatasets()),
                                new PagedRequest()
                                        .setFirstPageOffset(cdMatchInput.getOffset())
                                        .setPageSize(cdMatchInput.getSize())
                        ).getResultList().stream()
                                .map(this::getNeuronLocationAttributeValue))
                .collect(Collectors.toList());
    }

    private String getNeuronLocationAttributeValue(Map<String, Object> n) {
        try {
            return (String) n.get(neuronLocationAttributeName);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

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
        NeuronSelector maskSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(maskLibraries)
                .addNames(maskPublishedNames)
                .addMipIDs(maskMipIds)
                .addDatasetLabels(maskDatasets)
                .addTags(maskTags)
                .addExcludedTags(maskExcludedTags);
        NeuronSelector targetSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(targetLibraries)
                .addNames(targetPublishedNames)
                .addMipIDs(targetMipIds)
                .addDatasetLabels(targetDatasets)
                .addTags(targetTags)
                .addExcludedTags(targetExcludedTags);
        List<Number> maskEntityIds = getNeuronEntityIds(maskSelector);
        NeuronsMatchFilter<R> neuronsMatchFilter = new NeuronsMatchFilter<R>()
                .setScoresFilter(matchScoresFilter)
                .setMaskEntityIds(maskEntityIds)
                .addTags(matchTags)
                .addExcludedTags(matchExcludedTags);

        return readMatches(neuronsMatchFilter, null, targetSelector, sortCriteriaList);
    }

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
        NeuronSelector maskSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(maskLibraries)
                .addNames(maskPublishedNames)
                .addMipIDs(maskMipIds)
                .addDatasetLabels(maskDatasets)
                .addTags(maskTags)
                .addExcludedTags(maskExcludedTags);
        NeuronSelector targetSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(targetLibraries)
                .addNames(targetPublishedNames)
                .addMipIDs(targetMipIds)
                .addDatasetLabels(targetDatasets)
                .addTags(targetTags)
                .addExcludedTags(targetExcludedTags);
        List<Number> targetEntityIds = getNeuronEntityIds(targetSelector);
        NeuronsMatchFilter<R> neuronsMatchFilter = new NeuronsMatchFilter<R>()
                .setScoresFilter(matchScoresFilter)
                .setTargetEntityIds(targetEntityIds)
                .addTags(matchTags)
                .addExcludedTags(matchExcludedTags);

        return readMatches(neuronsMatchFilter, maskSelector, null, sortCriteriaList);
    }

    private List<Number> getNeuronEntityIds(NeuronSelector neuronSelector) {
        if (neuronSelector.isNotEmpty()) {
            return neuronMetadataDao.findNeurons(neuronSelector, new PagedRequest())
                    .getResultList().stream()
                    .map(AbstractNeuronEntity::getEntityId)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private List<R> readMatches(NeuronsMatchFilter<R> matchesFilter,
                                NeuronSelector maskSelector,
                                NeuronSelector targetSelector,
                                List<SortCriteria> sortCriteriaList) {
        PagedRequest pagedRequest = new PagedRequest().setSortCriteria(sortCriteriaList).setPageSize(PAGE_SIZE);
        List<R> matches = new ArrayList<>();
        for (long offset = 0; ; offset += PAGE_SIZE) {
            PagedResult<R> currentMatches = neuronMatchesDao.findNeuronMatches(
                    matchesFilter,
                    maskSelector,
                    targetSelector,
                    pagedRequest.setFirstPageOffset(offset)
            );
            if (currentMatches.isEmpty()) {
                break;
            }
            matches.addAll(currentMatches.getResultList());
        }
        return matches;
    }
}
