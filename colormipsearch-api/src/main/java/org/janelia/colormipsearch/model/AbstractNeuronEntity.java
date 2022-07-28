package org.janelia.colormipsearch.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.model.annotations.DoNotPersist;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

@PersistenceInfo(storeName ="neuronMetadata")
public abstract class AbstractNeuronEntity extends AbstractBaseEntity {

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
    // computeFileData holds local files used either for precompute or upload
    private final Map<ComputeFileType, FileData> computeFiles = new HashMap<>();
    // neuronFiles holds S3 files used by the NeuronBridge app
    private final Map<FileType, FileData> neuronFiles = new HashMap<>();

    @JsonProperty("id")
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

    public String getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(String sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<ComputeFileType, FileData> getComputeFiles() {
        return computeFiles;
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

    @JsonProperty("files")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<FileType, FileData> getNeuronFiles() {
        return neuronFiles;
    }

    public void resetNeuronFiles(Set<FileType> ts) {
        ts.forEach(neuronFiles::remove);
    }

    public String getNeuronFileName(FileType t) {
        FileData f = neuronFiles.get(t);
        return f != null ? f.getName() : null;
    }

    public boolean hasNeuronFile(FileType t) {
        return neuronFiles.containsKey(t);
    }

    public FileData getNeuronFileData(FileType t) {
        return neuronFiles.get(t);
    }

    public void setNeuronFileData(FileType t, FileData fd) {
        if (fd != null) {
            neuronFiles.put(t, fd);
        } else {
            neuronFiles.remove(t);
        }
    }

    public abstract AbstractNeuronEntity duplicate();

    public List<Pair<String, ?>> updatableFields() {
        return Arrays.asList(
                ImmutablePair.of("libraryName", getLibraryName()),
                ImmutablePair.of("computeFiles", getComputeFiles())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AbstractNeuronEntity that = (AbstractNeuronEntity) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(mipId, that.mipId)
                .append(getComputeFileData(ComputeFileType.InputColorDepthImage), that.getComputeFileData(ComputeFileType.InputColorDepthImage))
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(mipId)
                .append(getComputeFileData(ComputeFileType.InputColorDepthImage))
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("mipId", mipId)
                .append("libraryName", libraryName)
                .toString();
    }

    protected <N extends AbstractNeuronEntity> void copyFrom(N that) {
        this.mipId = that.getMipId();
        this.alignmentSpace = that.getAlignmentSpace();
        this.libraryName = that.getLibraryName();
        this.publishedName = that.getPublishedName();
        this.sourceRefId = that.getSourceRefId();
        this.computeFiles.clear();
        this.computeFiles.putAll(that.getComputeFiles());
        this.neuronFiles.clear();
        this.neuronFiles.putAll(that.getNeuronFiles());
    }

}
