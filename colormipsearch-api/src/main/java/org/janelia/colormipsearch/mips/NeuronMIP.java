package org.janelia.colormipsearch.mips;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.FileData;

/**
 *
 * @param <N> neuron type
 * @param <P> neuron mip image pixel type
 */
public class NeuronMIP<N extends AbstractNeuronEntity, P extends Type<P>> {
    private final N neuronInfo;
    private final FileData imageFileData;
    private final RandomAccessibleInterval<P> imageArray;

    public NeuronMIP(N neuronInfo, FileData imageFileData, RandomAccessibleInterval<P> imageArray) {
        this.neuronInfo = neuronInfo;
        this.imageFileData = imageFileData;
        this.imageArray = imageArray;
    }

    public N getNeuronInfo() {
        return neuronInfo;
    }

    public RandomAccessibleInterval<P> getImageArray() {
        return imageArray;
    }

    public boolean hasImageArray() {
        return imageArray != null;
    }

    public boolean hasNoImageArray() {
        return imageArray == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        NeuronMIP<?, ?> neuronMIP = (NeuronMIP<?, ?>) o;

        return new EqualsBuilder()
                .append(neuronInfo, neuronMIP.neuronInfo)
                .append(imageFileData, neuronMIP.imageFileData)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(neuronInfo).append(imageFileData).toHashCode();
    }
}
