package org.janelia.colormipsearch.dataio.db;

import java.util.List;

import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dataio.NeuronMatchesRemover;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBNeuronMatchesRemover<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>>
        implements NeuronMatchesRemover<R> {

    private static final Logger LOG = LoggerFactory.getLogger(DBNeuronMatchesRemover.class);

    private final NeuronMatchesDao<R> neuronMatchesDao;
    private final boolean archiveMatchesOnDelete;

    public DBNeuronMatchesRemover(NeuronMatchesDao<R> neuronMatchesDao, boolean archiveMatchesOnDelete) {
        this.neuronMatchesDao = neuronMatchesDao;
        this.archiveMatchesOnDelete = archiveMatchesOnDelete;
    }

    @Override
    public long delete(List<R> matches) {
        if (archiveMatchesOnDelete) {
            LOG.info("Archiving {} matches", matches.size());
            return neuronMatchesDao.archiveMatches(matches);
        } else {
            LOG.info("Removing {} matches", matches.size());
            return neuronMatchesDao.deleteMatches(matches);
        }
    }
}
