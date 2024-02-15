package org.janelia.colormipsearch.dataio.db;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.EntityField;

/**
 * This implementation of the ResultMatchesWriter tries to update the scores for an existing ColorDepth match,
 * if the match already exists.
 * If the match does not exist it will create it.
 *
 * @param <R> match type
 */
public class DBCDScoresOnlyWriter<R extends CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> implements NeuronMatchesWriter<R> {

    private final NeuronMatchesDao<R> neuronMatchesDao;

    private final List<Function<R, EntityField<?>>> fieldsToUpdate =
            Arrays.asList(
                    m -> new EntityField<>("sessionRefId", false, m.getSessionRefId()),
                    m -> new EntityField<>("mirrored", false, m.isMirrored()),
                    m -> new EntityField<>("matchingPixels", false, m.getMatchingPixels()),
                    m -> new EntityField<>("matchingPixelsRatio", false, m.getMatchingPixelsRatio()),
                    m -> new EntityField<>("normalizedScore", false, m.getNormalizedScore()),
                    m -> new EntityField<>("tags", true, m.getTags())
            );

    public DBCDScoresOnlyWriter(NeuronMatchesDao<R> neuronMatchesDao) {
        this.neuronMatchesDao = neuronMatchesDao;
    }

    public long write(List<R> matches) {
        return neuronMatchesDao.createOrUpdateAll(matches, fieldsToUpdate);
    }

    @Override
    public long writeUpdates(List<R> matches, List<Function<R, Pair<String, ?>>> fieldSelectors) {
        return neuronMatchesDao.updateExistingMatches(matches, fieldSelectors);
    }
}
