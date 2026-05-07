package org.janelia.colormipsearch.mips;

import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * This is used for loading
 *
 * @param <N> neuron type
 */
@FunctionalInterface
public interface NeuronMIPLoader<N extends AbstractNeuronEntity> {
    NeuronMIP<N> loadMIP(N neuron, ComputeFileType computeFileType);
}
