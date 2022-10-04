package org.janelia.colormipsearch.model;

import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> updateableFieldValues() {
        Map<String, Object> dict = new HashMap<>(super.updateableFieldValues());
        dict.put("neuronType", neuronType);
        dict.put("neuronInstance", neuronInstance);
        return dict;
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
        getComputeFiles().forEach((ft, fd) -> n.setNeuronComputeFile(ft, fd.getFileName()));
        getProcessedTags().forEach(n::putProcessedTags);
        return n;
    }
}
