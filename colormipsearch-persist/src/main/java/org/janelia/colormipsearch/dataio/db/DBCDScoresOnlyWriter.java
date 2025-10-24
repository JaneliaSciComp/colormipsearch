package org.janelia.colormipsearch.dataio.db;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
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
                    m -> new EntityField<>("sessionRefId", m.getSessionRefId()),
                    m -> new EntityField<>("mirrored", m.isMirrored()),
                    m -> new EntityField<>("matchingPixels", m.getMatchingPixels()),
                    m -> new EntityField<>("matchingPixelsRatio", m.getMatchingPixelsRatio()),
                    m -> new EntityField<>("normalizedScore", m.getNormalizedScore()),
                    m -> new EntityField<>("tags", m.getTags(), EntityField.FieldOp.ADD_TO_SET)
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

    @Override
    public long bulkWriteUpdates(List<R> matches, List<EntityField<?>> fieldUpdates) {
        NeuronsMatchFilter<R> neuronsMatchFilter = new NeuronsMatchFilter<>();
        if (matches != null) {
            neuronsMatchFilter.setMatchEntityIds(matches.stream().map(AbstractBaseEntity::getEntityId).collect(Collectors.toSet()));
        }
        return neuronMatchesDao.updateAll(neuronsMatchFilter, fieldUpdates);
    }
}
