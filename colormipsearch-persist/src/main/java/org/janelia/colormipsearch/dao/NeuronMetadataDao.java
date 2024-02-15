package org.janelia.colormipsearch.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EntityField;
import org.janelia.colormipsearch.model.ProcessingType;

public interface NeuronMetadataDao<N extends AbstractNeuronEntity> extends Dao<N> {
    /**
     * Create or update neuron. If a neuron exists, update it otherwise create it.
     * @param neuron to create or update.
     */
    N createOrUpdate(N neuron);

    /**
     * Search neurons that match the provided selection criteria.
     *
     * @param neuronSelector neuron selection criteria
     * @param pageRequest pagination parameters
     * @return
     */
    PagedResult<N> findNeurons(NeuronSelector neuronSelector, PagedRequest pageRequest);

    /**
     * Find all distinct values for the specified attributes using the given neuron selector for filtering.
     *
     * @param attributeNames neuron distinct attributes that are being searched
     * @param neuronSelector neuron filter
     * @param pagedRequest
     * @return
     */
    PagedResult<Map<String, Object>> findDistinctNeuronAttributeValues(List<String> attributeNames,
                                                                       NeuronSelector neuronSelector,
                                                                       PagedRequest pagedRequest);

    /**
     * Add processing tags for the specified neuron IDs.
     *
     * @param neuronMIPIds
     * @param processingType
     * @param tags
     */
    void addProcessingTagsToMIPIDs(Collection<String> neuronMIPIds, ProcessingType processingType, Set<String> tags);

    /**
     * Update multiple entries based on the neuronSelector
     *
     * @param neuronSelector neuron criteria
     * @param fieldsToUpdate fields to update
     * @return
     */
    long updateAll(NeuronSelector neuronSelector, Map<String, EntityFieldValueHandler<?>> fieldsToUpdate);

    /**
     * Update specified fields for the given neurons.
     * @param neurons
     * @param fieldsToUpdateSelectors
     * @return
     */
    long updateExistingNeurons(List<N> neurons, List<Function<N, EntityField<?>>> fieldsToUpdateSelectors);
}
