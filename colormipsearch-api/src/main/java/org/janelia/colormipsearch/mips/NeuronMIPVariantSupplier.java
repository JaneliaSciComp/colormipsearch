package org.janelia.colormipsearch.mips;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is used for loading compute variants
 *
 * @param <N> neuron type
 */
public class NeuronMIPVariantSupplier<N extends AbstractNeuronEntity> implements ComputeVariantImageSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(NeuronMIPVariantSupplier.class);

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

    @Override
    public ImageArray getImage() {
        LOG.debug("Load {}:{} for {}", computeFileType, getName(), neuron);
        return NeuronMIPUtils.getImageArray(neuronMIPLoader.loadMIP(neuron, computeFileType));
    }
}
