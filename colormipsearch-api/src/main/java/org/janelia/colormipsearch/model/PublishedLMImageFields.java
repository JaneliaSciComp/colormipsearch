package org.janelia.colormipsearch.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

/**
 * This class holds the published image attributes created in order to avoid the recursive reference to
 * @PublishedLMImage for GAL4 expressions.
 *
 * The reason for the typeInfo annotation here is to deal with the wrong classname persisted in the collection.
 * The class name used is the JACS class: org.janelia.model.domain.sample.PublishedLMImage so the published
 * class will be deserialized based on the context the deserialization is invoked from.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class PublishedLMImageFields extends AbstractBaseEntity {
    private String sampleRef;
    private String line;
    private String area;
    private String tile;
    private String originalLine;
    private String slideCode;
    private String objective;
    private String alignmentSpace;
    private String releaseName;
    private final Map<String, String> files = new HashMap<>();

    public String getSampleRef() {
        return sampleRef;
    }

    public void setSampleRef(String sampleRef) {
        this.sampleRef = sampleRef;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getTile() {
        return tile;
    }

    public void setTile(String tile) {
        this.tile = tile;
    }

    public String getOriginalLine() {
        return originalLine;
    }

    public void setOriginalLine(String originalLine) {
        this.originalLine = originalLine;
    }

    public String getSlideCode() {
        return slideCode;
    }

    public void setSlideCode(String slideCode) {
        this.slideCode = slideCode;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public boolean hasObjective() {
        return StringUtils.isNotBlank(objective);
    }

    public String getAlignmentSpace() {
        return alignmentSpace;
    }

    public void setAlignmentSpace(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    void setFiles(Map<String, String> files) {
        if (files != null) {
            this.files.putAll(files);
        }
    }

    public void addFile(String fileType, String file) {
        if (StringUtils.isNotBlank(fileType) && StringUtils.isNotBlank(file)) {
            this.files.put(fileType, file);
        }
    }

    public String getFile(String fileType) {
        return this.files.get(fileType);
    }

    public boolean hasFile(String fileType) {
        return this.files.containsKey(fileType);
    }

    @JsonProperty("creationDate")
    public Date getCreatedDate() {
        return super.getCreatedDate();
    }

}
