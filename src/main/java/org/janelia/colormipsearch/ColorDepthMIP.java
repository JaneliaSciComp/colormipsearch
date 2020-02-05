package org.janelia.colormipsearch;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

class ColorDepthMIP {
    @JsonProperty("_id")
    String id;
    @JsonProperty
    String name;
    @JsonProperty
    String filepath;
    @JsonProperty
    String objective;
    @JsonProperty
    String alignmentSpace;
    @JsonProperty
    String anatomicalArea;
    @JsonProperty
    String channelNumber;
    @JsonProperty
    String publicImageUrl;
    @JsonProperty
    String publicThumbnailUrl;
    @JsonProperty
    List<String> libraries;
    @JsonProperty
    String sampleRef;
    @JsonProperty
    CDMIPSample sample;

    String findLibrary() {
        if (CollectionUtils.isEmpty(libraries)) {
            return null;
        } else {
            return libraries.stream().findFirst().orElse(null);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .toString();
    }
}