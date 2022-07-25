package org.janelia.colormipsearch.dataio.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractMatch;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;

public class DBNeuronMatchesReader<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata, R extends AbstractMatch<M, T>> implements NeuronMatchesReader<M, T, R> {

    private final static int PAGE_SIZE = 10000;

    private final NeuronMetadataDao<AbstractNeuronMetadata> neuronMetadataDao;
    private final NeuronMatchesDao<M, T, R> neuronMatchesDao;

    public DBNeuronMatchesReader(Config config) {
        this.neuronMetadataDao = DaosProvider.getInstance(config).getNeuronMetadataDao();
        this.neuronMatchesDao = DaosProvider.getInstance(config).getNeuronMatchesDao();
    }

    @Override
    public List<String> listMatchesLocations(List<DataSourceParam> matchesSource) {
        return matchesSource.stream()
                        .flatMap(cdMatchInput -> neuronMetadataDao.findNeurons(
                                new NeuronSelector().setLibraryName(cdMatchInput.getLocation()),
                                new PagedRequest()
                                        .setFirstPageOffset(cdMatchInput.getOffset())
                                        .setPageSize(cdMatchInput.getSize())
                        ).getResultList().stream().map(AbstractNeuronMetadata::getId))
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<R> readMatchesForMasks(String maskLibrary,
                                       List<String> maskMipIds,
                                       NeuronsMatchFilter<R> matchesFilter,
                                       List<SortCriteria> sortCriteriaList) {
        NeuronSelector maskSelector = new NeuronSelector().setLibraryName(maskLibrary).addMipIDs(maskMipIds);
        maskSelector.addEntityIds(getNeuronEntityIds(maskSelector));
        NeuronSelector targetSelector = new NeuronSelector();
        return readMatches(matchesFilter, maskSelector, targetSelector, sortCriteriaList);
    }

    @Override
    public List<R> readMatchesForTargets(String targetLibrary,
                                         List<String> targetMipIds,
                                         NeuronsMatchFilter<R> matchesFilter,
                                         List<SortCriteria> sortCriteriaList) {
        NeuronSelector maskSelector = new NeuronSelector();
        NeuronSelector targetSelector = new NeuronSelector().setLibraryName(targetLibrary).addMipIDs(targetMipIds);
        targetSelector.addEntityIds(getNeuronEntityIds(targetSelector));
        return readMatches(matchesFilter, maskSelector, targetSelector, sortCriteriaList);
    }

    private List<Number> getNeuronEntityIds(NeuronSelector neuronSelector) {
        if (neuronSelector.isEmpty()) {
            return Collections.emptyList();
        } else {
            return neuronMetadataDao.findNeurons(neuronSelector, new PagedRequest())
                    .getResultList().stream()
                    .map(AbstractNeuronMetadata::getEntityId)
                    .collect(Collectors.toList());
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
