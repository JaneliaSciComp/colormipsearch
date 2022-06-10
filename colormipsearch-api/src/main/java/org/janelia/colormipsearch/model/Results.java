package org.janelia.colormipsearch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.janelia.colormipsearch.api_v2.JsonRequired;

public class Results<T> {
    @JsonRequired
    @JsonProperty
    public final T results;

    @JsonCreator
    public Results(@JsonProperty("results") T results) {
        this.results = results;
    }

    public T getResults() {
        return results;
    }

    public boolean hasResults() {
        return results != null;
    }

}
