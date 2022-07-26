package org.janelia.colormipsearch.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

@PersistenceInfo(storeName ="neuronMetadata")
public abstract class AbstractNeuronMetadata extends AbstractBaseEntity {

    public static class Builder<N extends AbstractNeuronMetadata> {

        private N n;

        public Builder(Supplier<N> source) {
            this.n = source.get();
        }

        public N get() {
            return n;
        }

        public Builder<N> entityId(Number entityId) {
            n.setEntityId(entityId);
            return this;
        }

        public Builder<N> id(String id) {
            n.setMipId(id);
            return this;
        }

        public Builder<N> publishedName(String name) {
            n.setPublishedName(name);
            return this;
        }

        public Builder<N> fileData(FileType ft, FileData fd) {
            n.setNeuronFileData(ft, fd);
            return this;
        }

        public Builder<N> computeFileData(ComputeFileType ft, FileData fd) {
            n.setComputeFileData(ft, fd);
            return this;
        }

        public Builder<N> library(String library) {
            n.setLibraryName(library);
            return this;
        }
    }

    private String mipId; // MIP ID - not to be confused with the entityId which is the primary key of this entity
    private String libraryName; // MIP library
    private String publishedName;
    private String alignmentSpace;
    private Gender gender;
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

    @JsonRequired
    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }

    @JsonRequired
    public String getPublishedName() {
        return publishedName;
    }

    public void setPublishedName(String publishedName) {
        this.publishedName = publishedName;
    }

    @JsonRequired
    public String getAlignmentSpace() {
        return alignmentSpace;
    }

    public void setAlignmentSpace(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
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

    public abstract String buildNeuronSourceName();

    public abstract AbstractNeuronMetadata duplicate();

    public void cleanupForRelease() {
        resetComputeFileData(EnumSet.allOf(ComputeFileType.class));
    }

    public List<Pair<String, ?>> updatableFields() {
        return Arrays.asList(
                ImmutablePair.of("libraryName", getLibraryName()),
                ImmutablePair.of("publishedName", getPublishedName()),
                ImmutablePair.of("alignmentSpace", getAlignmentSpace()),
                ImmutablePair.of("gender", getGender()),
                ImmutablePair.of("computeFiles", getComputeFiles())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AbstractNeuronMetadata that = (AbstractNeuronMetadata) o;

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
                .append("id", mipId)
                .append("libraryName", libraryName)
                .append("publishedName", publishedName)
                .toString();
    }

    protected <N extends AbstractNeuronMetadata> void copyFrom(N that) {
        this.mipId = that.getMipId();
        this.libraryName = that.getLibraryName();
        this.publishedName = that.getPublishedName();
        this.alignmentSpace = that.getAlignmentSpace();
        this.gender = that.getGender();
        this.computeFiles.clear();
        this.computeFiles.putAll(that.getComputeFiles());
        this.neuronFiles.clear();
        this.neuronFiles.putAll(that.getNeuronFiles());
    }

}
