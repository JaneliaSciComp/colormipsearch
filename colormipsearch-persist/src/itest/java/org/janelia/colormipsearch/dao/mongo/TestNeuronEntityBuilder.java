package org.janelia.colormipsearch.dao.mongo;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.ProcessingType;

class TestNeuronEntityBuilder<N extends AbstractNeuronEntity> {

    private N n;

    public TestNeuronEntityBuilder(Supplier<N> source) {
        this.n = source.get();
    }

    public N get() {
        return n;
    }

    public TestNeuronEntityBuilder<N> entityId(Number entityId) {
        n.setEntityId(entityId);
        return this;
    }

    public TestNeuronEntityBuilder<N> mipId(String id) {
        n.setMipId(id);
        return this;
    }

    public TestNeuronEntityBuilder<N> publishedName(String name) {
        n.setPublishedName(name);
        return this;
    }

    public TestNeuronEntityBuilder<N> computeFileData(ComputeFileType ft, FileData fd) {
        n.setComputeFileData(ft, fd);
        return this;
    }

    public TestNeuronEntityBuilder<N> library(String library) {
        n.setLibraryName(library);
        return this;
    }

    public TestNeuronEntityBuilder<N> addTags(Collection<String> tags) {
        n.addAllTags(tags);
        return this;
    }

    public TestNeuronEntityBuilder<N> addProcessedTags(ProcessingType processingType, Set<String> tags) {
        n.addProcessedTags(processingType, tags);
        return this;
    }
}
