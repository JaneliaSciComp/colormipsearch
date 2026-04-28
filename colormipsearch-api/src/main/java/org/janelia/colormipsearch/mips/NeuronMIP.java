package org.janelia.colormipsearch.mips;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.FileData;

public class NeuronMIP<N extends AbstractNeuronEntity> {
    private final N neuronInfo;
    private final FileData imageFileData;
    private final ImageArray imageArray;

    public NeuronMIP(N neuronInfo, FileData imageFileData, ImageArray imageArray) {
        this.neuronInfo = neuronInfo;
        this.imageFileData = imageFileData;
        this.imageArray = imageArray;
    }

    public N getNeuronInfo() {
        return neuronInfo;
    }

    public ImageArray getImageArray() {
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

        return new EqualsBuilder().append(neuronInfo, neuronMIP.neuronInfo).append(imageFileData, neuronMIP.imageFileData).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(neuronInfo).append(imageFileData).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("neuronInfo", neuronInfo)
                .append("imageFileData", imageFileData)
                .toString();
    }
}
