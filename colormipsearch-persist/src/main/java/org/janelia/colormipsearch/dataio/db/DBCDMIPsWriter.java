package org.janelia.colormipsearch.dataio.db;

import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class DBCDMIPsWriter implements CDMIPsWriter {

    private final NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao;

    public DBCDMIPsWriter(Config config) {
        this.neuronMetadataDao = DaosProvider.getInstance(config).getNeuronMetadataDao();
    }

    @Override
    public void open() {
        // nothing to do for the DB writer
    }

    @Override
    public void write(AbstractNeuronEntity neuronMetadata) {
        neuronMetadataDao.createOrUpdate(neuronMetadata);
    }

    @Override
    public void close() {
        // nothing to do for the DB writer
    }
}