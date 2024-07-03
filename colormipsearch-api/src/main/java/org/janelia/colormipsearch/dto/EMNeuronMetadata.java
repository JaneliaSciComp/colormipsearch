package org.janelia.colormipsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EMNeuronMetadata extends AbstractNeuronMetadata {
    /** This will be used to generate the export file name for PPPM */
    private String emRefId;
    private String neuronType;
    private String neuronInstance;
    private String state;

    @Override
    public String getTypeDiscriminator() {
        return "EMImage";
    }

    @JsonIgnore
    public String getEmRefId() {
        return emRefId;
    }

    public void setEmRefId(String emRefId) {
        this.emRefId = emRefId;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
