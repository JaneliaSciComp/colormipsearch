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
                                        .addExcludedTags(cdMatchInput.getExcludedTags())
                                        .addDatasetLabels(cdMatchInput.getDatasets())
                                        .addAnnotations(cdMatchInput.getAnnotations())
                                        .addExcludedAnnotations(cdMatchInput.getExcludedAnnotations()),
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
                                     DataSourceParam maskDataSource,
                                     DataSourceParam targetDataSource,
                                     Collection<String> matchTags,
                                     Collection<String> matchExcludedTags,
                                     ScoresFilter matchScoresFilter,
                                     List<SortCriteria> sortCriteriaList) {
        NeuronSelector maskSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(maskDataSource.getLibraries())
                .addNames(maskDataSource.getNames())
                .addMipIDs(maskDataSource.getMipIDs())
                .addDatasetLabels(maskDataSource.getDatasets())
                .addTags(maskDataSource.getTags())
                .addExcludedTags(maskDataSource.getExcludedTags())
                .addAnnotations(maskDataSource.getAnnotations())
                .addExcludedAnnotations(maskDataSource.getAnnotations())
                .addProcessedTags(maskDataSource.getProcessingTags());
        NeuronSelector targetSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(targetDataSource.getLibraries())
                .addNames(targetDataSource.getNames())
                .addMipIDs(targetDataSource.getMipIDs())
                .addDatasetLabels(targetDataSource.getDatasets())
                .addTags(targetDataSource.getTags())
                .addExcludedTags(targetDataSource.getExcludedTags())
                .addAnnotations(targetDataSource.getAnnotations())
                .addExcludedAnnotations(targetDataSource.getExcludedAnnotations())
                .addProcessedTags(targetDataSource.getProcessingTags());
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
                                       DataSourceParam maskDataSource,
                                       DataSourceParam targetDataSource,
                                       Collection<String> matchTags,
                                       Collection<String> matchExcludedTags,
                                       ScoresFilter matchScoresFilter,
                                       List<SortCriteria> sortCriteriaList) {
        NeuronSelector maskSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(maskDataSource.getLibraries())
                .addNames(maskDataSource.getNames())
                .addMipIDs(maskDataSource.getMipIDs())
                .addDatasetLabels(maskDataSource.getDatasets())
                .addTags(maskDataSource.getTags())
                .addExcludedTags(maskDataSource.getExcludedTags())
                .addAnnotations(maskDataSource.getAnnotations())
                .addExcludedAnnotations(maskDataSource.getAnnotations());
        NeuronSelector targetSelector = new NeuronSelector()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(targetDataSource.getLibraries())
                .addNames(targetDataSource.getNames())
                .addMipIDs(targetDataSource.getMipIDs())
                .addDatasetLabels(targetDataSource.getDatasets())
                .addTags(targetDataSource.getTags())
                .addExcludedTags(targetDataSource.getExcludedTags())
                .addAnnotations(targetDataSource.getAnnotations())
                .addExcludedAnnotations(targetDataSource.getExcludedAnnotations());
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
