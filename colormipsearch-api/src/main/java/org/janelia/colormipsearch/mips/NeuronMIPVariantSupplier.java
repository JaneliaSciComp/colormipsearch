package org.janelia.colormipsearch.mips;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.cds.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * This is used for loading compute variants
 *
 * @param <N> neuron type
 * @param <P> pixel type
 */
public class NeuronMIPVariantSupplier<N extends AbstractNeuronEntity, P extends IntegerType<P>> implements ComputeVariantImageSupplier<P> {

    private final N neuron;
    private final ComputeFileType computeFileType;
    private final NeuronMIPLoader<N> neuronMIPLoader;

    NeuronMIPVariantSupplier(N neuron, ComputeFileType computeFileType, NeuronMIPLoader<N> neuronMIPLoader) {
        this.neuron = neuron;
        this.computeFileType = computeFileType;
        this.neuronMIPLoader = neuronMIPLoader;
    }

    @Override
    public String getName() {
        return neuron.getComputeFileName(computeFileType);
    }

    ComputeFileType getComputeFileType() {
        return computeFileType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RandomAccessibleInterval<P> getImage() {
        return (RandomAccessibleInterval<P>) NeuronMIPUtils.getImageArray(neuronMIPLoader.loadMIP(neuron, computeFileType));
    }
}
