package org.janelia.colormipsearch.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

@PersistenceInfo(storeName ="neuronMetadata")
public abstract class AbstractNeuronEntity extends AbstractBaseEntity {
    public static final String NO_CONSENSUS = "No Consensus";

    // MIP ID that comes from the Workstation (JACS).
    // This does not uniquely identify the metadata because there may be multiple images,
    // such as segmentation or FL images, that will be actually used for matching this MIP ID.
    // Do not mix this with the entityId which is the primary key of this entity
    private String mipId;
    // MIP alignment space
    private String alignmentSpace;
    // MIP library name
    private String libraryName;
    // neuron published name - this field is required during the gradient score process for selecting the top ranked matches
    private String publishedName;
    // Source Ref ID - either the LM Sample Reference ID or EM Body Reference ID.
    // This will be used to identify the matched neurons for PPP since the color depth MIPs are not
    // part of the PPP match process at all.
    private String sourceRefId;
    private List<String> neuronTerms;
    // computeFileData holds local files used either for precompute or upload
    private final Map<ComputeFileType, FileData> computeFiles = new HashMap<>();
    // processed tags holds the corresponding processing tag used for the ColorDepthSearch or PPPM import
    // to mark that the entity was part of the process;
    private final Map<ProcessingType, Set<String>> processedTags = new HashMap<>();
    private final Set<String> datasetLabels = new HashSet<>();
    private Set<String> validationErrors;

    public String getMipId() {
        return mipId;
    }

    public void setMipId(String mipId) {
        this.mipId = mipId;
    }

    public boolean hasMipID() {
        return StringUtils.isNotBlank(mipId);
    }

    @JsonIgnore
    public abstract String getNeuronId();

    public String getAlignmentSpace() {
        return alignmentSpace;
    }

    public void setAlignmentSpace(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    public boolean hasAlignmentSpace() {
        return StringUtils.isNotBlank(alignmentSpace);
    }

    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }

    public String getPublishedName() {
        return publishedName;
    }

    public void setPublishedName(String publishedName) {
        this.publishedName = publishedName;
    }

    public boolean hasPublishedName() {
        return StringUtils.isNotBlank(publishedName);
    }

    @JsonIgnore
    public boolean isValid() {
        return hasPublishedName();
    }

    public String getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(String sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    @JsonIgnore
    @Nullable
    public String getSourceRefIdOnly() {
        if (StringUtils.isBlank(sourceRefId)) {
            return null;
        } else {
            int separatorIndex = sourceRefId.indexOf('#');
            if (separatorIndex == -1) {
                return sourceRefId;
            } else {
                return sourceRefId.substring(separatorIndex+1);
            }
        }
    }

    public List<String> getNeuronTerms() {
        return neuronTerms;
    }

    public void setNeuronTerms(List<String> neuronTerms) {
        this.neuronTerms = neuronTerms;
    }

    @JsonProperty
    public Map<ComputeFileType, FileData> getComputeFiles() {
        return computeFiles;
    }

    void setComputeFiles(Map<ComputeFileType, FileData> computeFiles) {
        if (computeFiles != null) {
            this.computeFiles.putAll(computeFiles);
        }
    }

    public FileData getComputeFileData(ComputeFileType t) {
        return computeFiles.get(t);
    }

    public void setComputeFileData(ComputeFileType t, FileData fd) {
        if (fd != null) {
            computeFiles.put(t, fd);
        } else {
            computeFiles.remove(t);
        }
    }

    public void resetComputeFileData(Set<ComputeFileType> ts) {
        ts.forEach(computeFiles::remove);
    }

    public String getComputeFileName(ComputeFileType t) {
        FileData f = computeFiles.get(t);
        return f != null ? f.getName() : null;
    }

    public boolean hasComputeFile(ComputeFileType t) {
        return computeFiles.containsKey(t);
    }

    public Set<String> getDatasetLabels() {
        return datasetLabels;
    }

    void setDatasetLabels(Set<String> datasetLabels) {
        if (datasetLabels != null) {
            this.datasetLabels.addAll(datasetLabels);
        }
    }

    public void addDatasetLabel(String datasetLabel) {
        if (StringUtils.isNotBlank(datasetLabel)) {
            this.datasetLabels.add(datasetLabel.trim());
        }
    }

    public void addDatasetLabels(Collection<String> datasetLabels) {
        if (datasetLabels != null) {
            datasetLabels.forEach(this::addDatasetLabel);
        }
    }

    public Set<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Set<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public boolean hasValidationErrors() {
        return validationErrors != null && validationErrors.size() > 0;
    }

    public void clearValidationErrors() {
        validationErrors = null;
    }

    public Map<ProcessingType, Set<String>> getProcessedTags() {
        return processedTags;
    }

    void setProcessedTags(Map<ProcessingType, Set<String>> processedTags) {
        if (processedTags != null) {
            this.processedTags.putAll(processedTags);
        }
    }

    public AbstractNeuronEntity addProcessedTags(ProcessingType processingType, Set<String> tags) {
        if (processingType != null && CollectionUtils.isNotEmpty(tags)) {
            synchronized (processedTags) {
                processedTags.computeIfAbsent(processingType, k -> new HashSet<>());
            }
            tags.stream().filter(StringUtils::isNotBlank).forEach(t -> processedTags.get(processingType).add(t));
        }
        return this;
    }

    public boolean hasAnyProcessedTag(ProcessingType processingType) {
        return CollectionUtils.isNotEmpty(processedTags.get(processingType));
    }

    public boolean hasProcessedTag(ProcessingType processingType, String tag) {
        return CollectionUtils.isNotEmpty(processedTags.get(processingType)) &&
                processedTags.get(processingType).contains(tag);
    }

    public boolean hasProcessedTags(ProcessingType processingType, Set<String> tags) {
        if (processingType == null || CollectionUtils.isEmpty(tags)) {
            // if the check does not make any sense return false
            return false;
        } else {
            return CollectionUtils.isNotEmpty(processedTags.get(processingType)) &&
                    processedTags.get(processingType).containsAll(tags);
        }
    }

    public abstract AbstractNeuronEntity duplicate();

    public abstract AbstractNeuronMetadata metadata();

    public List<EntityField<?>> updateableFieldValues() {
        List<EntityField<?>> fieldList = new ArrayList<>();
        fieldList.add(new EntityField<>("alignmentSpace", false, alignmentSpace));
        fieldList.add(new EntityField<>("libraryName", false, libraryName));
        fieldList.add(new EntityField<>("publishedName", false, publishedName));
        fieldList.add(new EntityField<>("mipId", false, getMipId()));
        fieldList.add(new EntityField<>("sourceRefId", false, sourceRefId));
        fieldList.add(new EntityField<>("neuronTerms", false, neuronTerms));
        fieldList.add(new EntityField<>("updatedDate", false, getUpdatedDate()));
        // datasetLabels is a collection but will always be replaced instead of appended to existing values
        fieldList.add(new EntityField<>("datasetLabels", false, datasetLabels));
        fieldList.add(new EntityField<>("validationErrors", true, validationErrors));
        computeFiles.forEach((ft, fd) ->
                fieldList.add(new EntityField<>("computeFiles." + ft.name(), false, fd)));
        processedTags.forEach((pt, t) ->
                fieldList.add(new EntityField<>("processedTags." + pt.name(), true, t)));
        return fieldList;
    }

    public List<EntityField<?>>  updateableFieldsOnInsert() {
        List<EntityField<?>> fieldList = new ArrayList<>();
        fieldList.add(new EntityField<>("class", false, getEntityClass()));
        fieldList.add(new EntityField<>("tags", true, getTags()));
        return fieldList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AbstractNeuronEntity that = (AbstractNeuronEntity) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(mipId, that.mipId)
                .append(getComputeFileData(ComputeFileType.SourceColorDepthImage), that.getComputeFileData(ComputeFileType.SourceColorDepthImage))
                .append(getComputeFileData(ComputeFileType.InputColorDepthImage), that.getComputeFileData(ComputeFileType.InputColorDepthImage))
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(mipId)
                .append(getComputeFileData(ComputeFileType.SourceColorDepthImage))
                .append(getComputeFileData(ComputeFileType.InputColorDepthImage))
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("entityId", getEntityId())
                .append("mipId", mipId)
                .append("publishedName", publishedName)
                .append("libraryName", libraryName)
                .append("alignmentSpace", alignmentSpace)
                .append("sourceImage", getComputeFileName(ComputeFileType.SourceColorDepthImage))
                .append("searchableImage", getComputeFileName(ComputeFileType.InputColorDepthImage))
                .toString();
    }

    protected <N extends AbstractNeuronEntity> void copyFrom(N that) {
        this.mipId = that.getMipId();
        this.alignmentSpace = that.getAlignmentSpace();
        this.libraryName = that.getLibraryName();
        this.publishedName = that.getPublishedName();
        this.sourceRefId = that.getSourceRefId();
        this.neuronTerms = that.getNeuronTerms();
        this.computeFiles.clear();
        this.computeFiles.putAll(that.getComputeFiles());
        this.addAllTags(that.getTags());
        this.setValidationErrors(that.getValidationErrors());
        this.setProcessedTags(that.getProcessedTags());
        this.addDatasetLabels(that.getDatasetLabels());
    }

}
