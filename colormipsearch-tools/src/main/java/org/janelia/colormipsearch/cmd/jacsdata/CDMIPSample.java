package org.janelia.colormipsearch.cmd.jacsdata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This is the representation of a JACS Sample.
 */
public class CDMIPSample {
    public static Map<String, CDMIPSample> indexByRef(List<CDMIPSample> lmLines) {
        return lmLines.stream().collect(Collectors.toMap(s -> "Sample#" + s.id, s -> s));
    }

    public static Map<String, CDMIPSample> indexBySampleName(List<CDMIPSample> lmLines) {
        // index LM samples
        // Note: it is possible to have multiple samples with the same name so resolve the conflict
        // by picking the first one found that is sageSynced
        return lmLines.stream()
                .collect(Collectors.toMap(
                        s -> s.name,
                        s -> s,
                        (s1, s2) -> {
                            if (s1.sageSynced != null && s1.sageSynced) {
                                return s1;
                            } else {
                                return s2;
                            }
                        })); // in case of conflict pick the first one with sageSynced
    }

    @JsonProperty("_id")
    public String id;
    @JsonProperty
    public String name;
    @JsonProperty
    public String dataSet;
    @JsonProperty
    public String gender;
    @JsonProperty
    public String mountingProtocol;
    @JsonProperty
    public String driver;
    @JsonProperty
    public String organism;
    @JsonProperty
    public String genotype;
    @JsonProperty
    public String flycoreId;
    @JsonProperty
    public String line;
    @JsonProperty
    public String slideCode;
    @JsonProperty
    public String publishingName;
    @JsonProperty
    public Boolean publishedToStaging;
    @JsonProperty
    public String publishingError;
    @JsonProperty
    public Boolean sageSynced;
    @JsonProperty
    public String publishedExternally;
    @JsonProperty
    public String crossBarcode;
    @JsonProperty
    public String status;
    @JsonProperty
    public String releaseLabel;
    @JsonProperty
    public List<String> publishedObjectives;

    public String lmLineName() {
        return publishingName != null ? publishingName : line;
    }

    public String sampleRef() {
        return "Sample#" + id;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .toString();
    }
}
