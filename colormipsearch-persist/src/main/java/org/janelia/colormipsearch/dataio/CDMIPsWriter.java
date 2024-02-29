package org.janelia.colormipsearch.dataio;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;

/**
 * Writer for Color Depth MIPs metadata.
 */
public interface CDMIPsWriter {
    /**
     * Open and prepare the writer.
     */
    void open();

    /**
     * Write all items.
     *
     * @param neuronEntities items to write
     */
    void write(List<? extends AbstractNeuronEntity> neuronEntities);

    /**
     * Write one item.
     *
     * @param neuronEntity item to write
     */
    void writeOne(AbstractNeuronEntity neuronEntity);

    /**
     * Add provided processing tags to the list of neurons.
     *
     * @param neuronEntities
     * @param processingType
     * @param tags
     */
    void addProcessingTags(Collection<? extends AbstractNeuronEntity> neuronEntities, ProcessingType processingType, Set<String> tags);

    /**
     * Finish all writes and close the writer.
     */
    void close();
}
