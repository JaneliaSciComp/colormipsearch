package org.janelia.colormipsearch.model;

import java.util.HashMap;
import java.util.Map;

import org.janelia.colormipsearch.dto.LMNeuronMetadata;
import org.janelia.colormipsearch.model.annotations.DoNotPersist;

public class LMNeuronEntity extends AbstractNeuronEntity {

    // LM internal line name - this could be useful when importing the mips into a JSON file
    // which later is used to copy the MIPs to the filestore.
    // Having the internal name in there can be helpful in generating the proper MIP name
    // for the JACS filestore.
    private String internalLineName;
    // LM slide code is required for selecting the top ranked matches during gradient scoring
    private String slideCode;
    // anatomicalArea, gender and objective required for uploading the imagery
    private String anatomicalArea;
    private Gender gender;
    private String objective;
    private Boolean notStaged;
    private String publishError;

    @Override
    public String getNeuronId() {
        return getSlideCode();
    }

    @DoNotPersist
    public String getInternalLineName() {
        return internalLineName;
    }

    public void setInternalLineName(String internalLineName) {
        this.internalLineName = internalLineName;
    }

    public String getSlideCode() {
        return slideCode;
    }

    public void setSlideCode(String slideCode) {
        this.slideCode = slideCode;
    }

    public String getAnatomicalArea() {
        return anatomicalArea;
    }

    public void setAnatomicalArea(String anatomicalArea) {
        this.anatomicalArea = anatomicalArea;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public Boolean getNotStaged() {
        return notStaged;
    }

    public void setNotStaged(Boolean notStaged) {
        this.notStaged = notStaged;
    }

    public String getPublishError() {
        return publishError;
    }

    public void setPublishError(String publishError) {
        this.publishError = publishError;
    }

    @Override
    public Map<String, Object> updateableFieldValues() {
        Map<String, Object> dict = new HashMap<>(super.updateableFieldValues());
        dict.put("slideCode", slideCode);
        dict.put("anatomicalArea", anatomicalArea);
        dict.put("gender", gender);
        dict.put("objective", objective);
        dict.put("notStaged", notStaged);
        dict.put("publishError", publishError);
        return dict;
    }

    @Override
    public LMNeuronEntity duplicate() {
        LMNeuronEntity n = new LMNeuronEntity();
        n.copyFrom(this);
        n.internalLineName = this.getInternalLineName();
        n.slideCode = this.getSlideCode();
        n.anatomicalArea = this.getAnatomicalArea();
        n.gender = this.getGender();
        n.objective = this.getObjective();
        n.notStaged = this.notStaged;
        n.publishError = this.getPublishError();
        return n;
    }

    @Override
    public LMNeuronMetadata metadata() {
        LMNeuronMetadata n = new LMNeuronMetadata();
        n.setInternalId(getEntityId());
        n.setAlignmentSpace(getAlignmentSpace());
        n.setMipId(getMipId());
        n.setLibraryName(getLibraryName());
        n.setPublishedName(getPublishedName());
        n.setSlideCode(slideCode);
        n.setAnatomicalArea(anatomicalArea);
        n.setGender(gender);
        n.setObjective(objective);
        getComputeFiles().forEach((ft, fd) -> n.setNeuronComputeFile(ft, fd.getFileName()));
        getProcessedTags().forEach(n::putProcessedTags);
        return n;
    }
}
