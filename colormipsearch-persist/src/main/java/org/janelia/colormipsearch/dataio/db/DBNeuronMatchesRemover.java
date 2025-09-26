package org.janelia.colormipsearch.dataio.db;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dataio.NeuronMatchesRemover;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class DBNeuronMatchesRemover<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>>
        implements NeuronMatchesRemover<R> {

    private final NeuronMatchesDao<R> neuronMatchesDao;
    private final boolean archiveMatchesOnDelete;

    public DBNeuronMatchesRemover(NeuronMatchesDao<R> neuronMatchesDao, boolean archiveMatchesOnDelete) {
        this.neuronMatchesDao = neuronMatchesDao;
        this.archiveMatchesOnDelete = archiveMatchesOnDelete;
    }

    @Override
    public long delete(List<R> matches) {
        if (archiveMatchesOnDelete) {
            return neuronMatchesDao.archiveMatches(matches);
        } else {
            return neuronMatchesDao.deleteMatches(matches);
        }
    }
}
