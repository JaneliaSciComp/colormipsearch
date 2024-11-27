package org.janelia.colormipsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.janelia.colormipsearch.results.GroupedItems;

/**
 * Metadata about result matches grouped by a given mask type.
 *
 * @param <T>
 */
public class ResultMatches<M extends AbstractNeuronMetadata, R extends AbstractMatchedTarget<? extends AbstractNeuronMetadata>> extends GroupedItems<M, R> {
    public ResultMatches() {
    }

    public ResultMatches(M key, List<R> items) {
        setKey(key);
        setItems(items);
    }

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
