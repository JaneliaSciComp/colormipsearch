package org.janelia.colormipsearch.model;

import java.util.ArrayList;
import java.util.List;

import org.janelia.colormipsearch.dto.EMNeuronMetadata;

public class EMNeuronEntity extends AbstractNeuronEntity {

    // neuronType and the neuronInstance are only for reference purposes here
    private String neuronType;
    private String neuronInstance;

    @Override
    public String getNeuronId() {
        return getPublishedName();
    }

    public String getNeuronType() {
        return neuronType;
    }

    public void setNeuronType(String neuronType) {
        this.neuronType = neuronType;
    }

    public String getNeuronInstance() {
        return neuronInstance;
    }

    public void setNeuronInstance(String neuronInstance) {
        this.neuronInstance = neuronInstance;
    }

    @Override
    public List<EntityField<?>> updateableFieldValues() {
        List<EntityField<?>> fieldList = new ArrayList<>(super.updateableFieldValues());
        fieldList.add(new EntityField<>("neuronType", false, neuronType));
        fieldList.add(new EntityField<>("neuronInstance", false, neuronInstance));
        return fieldList;
    }

    @Override
    public EMNeuronEntity duplicate() {
        EMNeuronEntity n = new EMNeuronEntity();
        n.copyFrom(this);
        n.neuronType = this.getNeuronType();
        n.neuronInstance = this.getNeuronInstance();
        return n;
    }

    @Override
    public EMNeuronMetadata metadata() {
        EMNeuronMetadata n = new EMNeuronMetadata();
        n.setInternalId(getEntityId());
        n.setEmRefId(getSourceRefIdOnly());
        n.setAlignmentSpace(getAlignmentSpace());
        n.setMipId(getMipId());
        n.setLibraryName(getLibraryName());
        n.setPublishedName(getPublishedName());
        n.setNeuronType(getNeuronType());
        n.setNeuronInstance(getNeuronInstance());
        n.setAnnotations(getNeuronTerms());
        getComputeFiles().forEach((ft, fd) -> n.setNeuronComputeFile(ft, fd.getFileName()));
        getProcessedTags().forEach(n::putProcessedTags);
        return n;
    }
}
