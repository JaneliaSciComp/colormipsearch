package org.janelia.colormipsearch.dataio;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EntityField;

public interface NeuronMatchesWriter<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> {
    long write(List<R> matches);
    long writeUpdates(List<R> matches, List<Function<R, Pair<String, ?>>> fieldSelectors);
    long bulkWriteUpdates(List<R> matches, List<EntityField<?>> fieldUpdates);
}
