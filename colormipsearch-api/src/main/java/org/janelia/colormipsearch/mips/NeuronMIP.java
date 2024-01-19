package org.janelia.colormipsearch.mips;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.FileData;

public class NeuronMIP<N extends AbstractNeuronEntity> {
    private final N neuronInfo;
    private final FileData imageFileData;
    private final ImageAccess<? extends RGBPixelType<?>> imageArray;

    public NeuronMIP(N neuronInfo, FileData imageFileData, ImageAccess<? extends RGBPixelType<?>> imageArray) {
        this.neuronInfo = neuronInfo;
        this.imageFileData = imageFileData;
        this.imageArray = imageArray;
    }

    public N getNeuronInfo() {
        return neuronInfo;
    }

    public ImageAccess<? extends RGBPixelType<?>> getImageArray() {
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
}
