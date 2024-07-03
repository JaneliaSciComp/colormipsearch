package org.janelia.colormipsearch.dataio.db;

import java.util.List;

import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dataio.CDMIPsReader;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class DBCDMIPsReader implements CDMIPsReader {

    private final NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao;

    public DBCDMIPsReader(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao) {
        this.neuronMetadataDao = neuronMetadataDao;
    }

    @Override
    public List<? extends AbstractNeuronEntity> readMIPs(DataSourceParam mipsDataSource) {
        return neuronMetadataDao.findNeurons(
                new NeuronSelector()
                        .setAlignmentSpace(mipsDataSource.getAlignmentSpace())
                        .addLibraries(mipsDataSource.getLibraries())
                        .addNames(mipsDataSource.getNames())
                        .addMipIDs(mipsDataSource.getMipIDs())
                        .addDatasetLabels(mipsDataSource.getDatasets())
                        .addTags(mipsDataSource.getTags())
                        .addExcludedTags(mipsDataSource.getExcludedTags())
                        .addAnnotations(mipsDataSource.getAnnotations())
                        .addExcludedAnnotations(mipsDataSource.getExcludedAnnotations()),
                new PagedRequest().setPageSize(mipsDataSource.getSize()).setFirstPageOffset(mipsDataSource.getOffset())
        ).getResultList();
    }

}
