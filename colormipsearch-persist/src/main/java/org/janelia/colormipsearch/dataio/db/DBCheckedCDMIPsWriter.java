package org.janelia.colormipsearch.dataio.db;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBCheckedCDMIPsWriter implements CDMIPsWriter {
    private static final Logger LOG = LoggerFactory.getLogger(DBCheckedCDMIPsWriter.class);

    private final NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao;

    public DBCheckedCDMIPsWriter(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao) {
        this.neuronMetadataDao = neuronMetadataDao;
    }

    @Override
    public void open() {
        // nothing to do for the DB writer
    }

    @Override
    public void write(List<? extends AbstractNeuronEntity> neuronEntities) {
        neuronEntities.forEach(neuronMetadataDao::createOrUpdate);
    }

    @Override
    public void writeOne(AbstractNeuronEntity neuronEntity) {
        neuronMetadataDao.createOrUpdate(neuronEntity);
    }

    @Override
    public void addProcessingTags(Collection<? extends AbstractNeuronEntity> neuronEntities, ProcessingType processingType, Set<String> tags) {
        neuronMetadataDao.addProcessingTagsToMIPIDs(
                neuronEntities.stream()
                        .filter(AbstractNeuronEntity::hasMipID)
                        .map(AbstractNeuronEntity::getMipId)
                        .collect(Collectors.toSet()),
                processingType,
                tags);
    }

    @Override
    public void close() {
        // nothing to do for the DB writer
    }
}
