package org.janelia.colormipsearch.model;

import java.util.ArrayList;
import java.util.List;

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
        fieldList.add(new EntityField<>("neuronType", neuronType));
        fieldList.add(new EntityField<>("neuronInstance", neuronInstance));
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

}
