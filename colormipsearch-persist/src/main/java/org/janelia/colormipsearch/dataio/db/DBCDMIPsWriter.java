package org.janelia.colormipsearch.dataio.db;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;

public class DBCDMIPsWriter implements CDMIPsWriter {

    private final NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao;

    public DBCDMIPsWriter(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao) {
        this.neuronMetadataDao = neuronMetadataDao;
    }

    @Override
    public void open() {
        // nothing to do for the DB writer
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(List<? extends AbstractNeuronEntity> neuronEntities) {
        List<AbstractNeuronEntity> toWrite = (List<AbstractNeuronEntity>) neuronEntities;
        neuronMetadataDao.saveAll(toWrite);
    }

    @Override
    public void writeOne(AbstractNeuronEntity neuronEntity) {
        neuronMetadataDao.save(neuronEntity);
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
