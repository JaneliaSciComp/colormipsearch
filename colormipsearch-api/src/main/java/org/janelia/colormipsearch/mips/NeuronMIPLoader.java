package org.janelia.colormipsearch.mips;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;

/**
 * This is used for loading
 *
 * @param <N> neuron type
 */
@FunctionalInterface
public interface NeuronMIPLoader<N extends AbstractNeuronEntity> {
    NeuronMIP<N> loadMIP(N neuron, ComputeFileType computeFileType);
}
