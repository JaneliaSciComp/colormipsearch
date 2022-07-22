package org.janelia.colormipsearch.dataio.db;

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
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractMatch;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;

public class DBNeuronMatchesReader<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata, R extends AbstractMatch<M, T>> implements NeuronMatchesReader<M, T, R> {

    private final NeuronMetadataDao<M> neuronMetadataDao;
    private final NeuronMatchesDao<M, T, R> neuronMatchesDao;

    public DBNeuronMatchesReader(Config config) {
        this.neuronMetadataDao = DaosProvider.getInstance(config).getNeuronMetadataDao();
        this.neuronMatchesDao = DaosProvider.getInstance(config).getNeuronMatchesDao();
    }

    @Override
    public List<String> listMatchesLocations(List<DataSourceParam> matchesSource) {
        return matchesSource.stream()
                        .flatMap(cdMatchInput -> neuronMetadataDao.findNeuronMatches(
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
        return neuronMatchesDao.findNeuronMatches(
                matchesFilter,
                new NeuronSelector().setLibraryName(maskLibrary).addMipIDs(maskMipIds),
                new NeuronSelector(),
                new PagedRequest().setSortCriteria(sortCriteriaList)
        ).getResultList();
    }

    @Override
    public List<R> readMatchesForTargets(String targetLibrary,
                                         List<String> targetMipIds,
                                         NeuronsMatchFilter<R> matchesFilter,
                                         List<SortCriteria> sortCriteriaList) {
        return neuronMatchesDao.findNeuronMatches(
                matchesFilter,
                new NeuronSelector(),
                new NeuronSelector().setLibraryName(targetLibrary).addMipIDs(targetMipIds),
                new PagedRequest().setSortCriteria(sortCriteriaList)
        ).getResultList();
    }
}
