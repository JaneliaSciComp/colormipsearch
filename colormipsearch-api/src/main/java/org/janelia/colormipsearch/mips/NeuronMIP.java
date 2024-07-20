package org.janelia.colormipsearch.mips;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 *
 * @param <N> neuron type
 */
public class NeuronMIP<N extends AbstractNeuronEntity> {
    private final N neuronInfo;
    private final ComputeFileType fileType;
    private final RandomAccessibleInterval<? extends IntegerType<?>> imageArray;

    public NeuronMIP(N neuronInfo, ComputeFileType fileType, RandomAccessibleInterval<? extends IntegerType<?>> imageArray) {
        this.neuronInfo = neuronInfo;
        this.fileType = fileType;
        this.imageArray = imageArray;
    }

    public N getNeuronInfo() {
        return neuronInfo;
    }

    public RandomAccessibleInterval<? extends IntegerType<?>> getImageArray() {
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

        NeuronMIP<?> neuronMIP = (NeuronMIP<?>) o;

        return new EqualsBuilder()
                .append(neuronInfo, neuronMIP.neuronInfo)
                .append(fileType, neuronMIP.fileType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(neuronInfo).append(fileType).toHashCode();
    }
}
