package org.janelia.colormipsearch.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GroupedMatchedEntities<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, R extends AbstractMatchEntity<M, T>> extends GroupedItems<M, R> {

    @JsonProperty("inputImage")
    @Override
    public M getKey() {
        return super.getKey();
    }

    @Override
    public void setKey(M key) {
        super.setKey(key);
    }

    @JsonProperty("results")
    @Override
    public List<R> getItems() {
        return super.getItems();
    }

    @Override
    public void setItems(List<R> items) {
        super.setItems(items);
    }
}
