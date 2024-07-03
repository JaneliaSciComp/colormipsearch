package org.janelia.colormipsearch.dataio.db;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.SetFieldValueHandler;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class DBNeuronMatchesWriter<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>>
        implements NeuronMatchesWriter<R> {

    private final NeuronMatchesDao<R> neuronMatchesDao;

    public DBNeuronMatchesWriter(NeuronMatchesDao<R> neuronMatchesDao) {
        this.neuronMatchesDao = neuronMatchesDao;
    }

    @Override
    public long write(List<R> matches) {
        neuronMatchesDao.saveAll(matches);
        // since save actually only inserts records we can simply
        // return the size of the input if there's no exception
        return matches.size();
    }

    @Override
    public long writeUpdates(List<R> matches, List<Function<R, Pair<String, ?>>> fieldSelectors) {
        return neuronMatchesDao.updateExistingMatches(matches, fieldSelectors);
    }
}
