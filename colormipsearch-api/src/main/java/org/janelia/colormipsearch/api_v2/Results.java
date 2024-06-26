package org.janelia.colormipsearch.api_v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Deprecated
public class Results<T> {
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
