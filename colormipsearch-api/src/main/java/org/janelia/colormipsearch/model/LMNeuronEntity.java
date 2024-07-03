package org.janelia.colormipsearch.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    private boolean isStaged() {
        return notStaged == null || !notStaged;
    }

    public String getPublishError() {
        return publishError;
    }

    public void setPublishError(String publishError) {
        this.publishError = publishError;
    }

    @JsonIgnore
    @Override
    public boolean isValid() {
        return super.isValid() && isStaged();
    }

    @Override
    public List<EntityField<?>> updateableFieldValues() {
        List<EntityField<?>> fieldList = new ArrayList<>(super.updateableFieldValues());
        fieldList.add(new EntityField<>("slideCode", false, slideCode));
        fieldList.add(new EntityField<>("anatomicalArea", false, anatomicalArea));
        fieldList.add(new EntityField<>("gender", false, gender));
        fieldList.add(new EntityField<>("objective", false, objective));
        fieldList.add(new EntityField<>("notStaged", false, notStaged));
        fieldList.add(new EntityField<>("publishError", false, publishError));
        return fieldList;
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
        n.setAnnotations(getNeuronTerms());
        getComputeFiles().forEach((ft, fd) -> n.setNeuronComputeFile(ft, fd.getFileName()));
        getProcessedTags().forEach(n::putProcessedTags);
        return n;
    }
}
