package org.janelia.colormipsearch.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.dto.AbstractMatchedTarget;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.annotations.DoNotPersist;

/**
 * This is the representation of a database persisted neurons match.
 * @param <M> mask neuron type
 * @param <T> target neuron type
 */
public abstract class AbstractMatchEntity<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> extends AbstractBaseEntity {

    private Number sessionRefId; // session reference - identifies the a CDS or a PPP run
    private Number maskImageRefId; // reference to the mask image
    private M maskImage;
    private Number matchedImageRefId;
    private T matchedImage;
    private boolean mirrored; // if true the matchedImage was mirrored
    private Map<MatchComputeFileType, FileData> matchComputeFiles = new HashMap<>();

    public Number getSessionRefId() {
        return sessionRefId;
    }

    public void setSessionRefId(Number sessionRefId) {
        this.sessionRefId = sessionRefId;
    }

    @DoNotPersist
    public M getMaskImage() {
        return maskImage;
    }

    public void setMaskImage(M maskImage) {
        this.maskImage = maskImage;
    }

    public void resetMaskImage() {
        this.maskImage = null;
    }

    @JsonIgnore
    public String getMaskMIPId() {
        return maskImage != null ? maskImage.getMipId() : null;
    }

    public Number getMaskImageRefId() {
        if (maskImage != null && maskImage.hasEntityId()) {
            return maskImage.getEntityId();
        } else {
            return maskImageRefId;
        }
    }

    public void setMaskImageRefId(Number maskImageRefId) {
        this.maskImageRefId = maskImageRefId;
    }

    public boolean hasMaskImageRefId() {
        return maskImage != null && maskImage.hasEntityId() || maskImageRefId != null;
    }

    @DoNotPersist
    @JsonProperty("image")
    public T getMatchedImage() {
        return matchedImage;
    }

    public void setMatchedImage(T matchedImage) {
        this.matchedImage = matchedImage;
    }

    public void resetMatchedImage() {
        this.matchedImage = null;
    }

    @JsonIgnore
    public String getMatchedMIPId() {
        return matchedImage != null ? matchedImage.getMipId() : null;
    }

    public Number getMatchedImageRefId() {
        if (matchedImage != null && matchedImage.hasEntityId()) {
            return matchedImage.getEntityId();
        } else {
            return matchedImageRefId;
        }
    }

    public void setMatchedImageRefId(Number matchedImageRefId) {
        this.matchedImageRefId = matchedImageRefId;
    }

    public boolean hasMatchedImageRefId() {
        return matchedImage != null && matchedImage.hasEntityId() || matchedImageRefId != null;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public Map<MatchComputeFileType, FileData> getMatchComputeFiles() {
        return matchComputeFiles;
    }

    public void setMatchComputeFiles(Map<MatchComputeFileType, FileData> matchComputeFiles) {
        this.matchComputeFiles = matchComputeFiles;
    }

    public FileData getMatchComputeFileData(MatchComputeFileType t) {
        return matchComputeFiles.get(t);
    }

    public void setMatchComputeFileData(MatchComputeFileType t, FileData fd) {
        if (fd != null) {
            matchComputeFiles.put(t, fd);
        } else {
            matchComputeFiles.remove(t);
        }
    }

    public void resetMatchComputeFiles() {
        matchComputeFiles.clear();
    }

    /**
     * This method only copies data that can be safely assigned to the destination fields;
     * that is why it doess not copy the mask and the target images since the type for
     * those may not coincide with the ones from the source
     *
     * @param that
     * @param <M1> destination mask type
     * @param <T1> destination target type
     * @param <R1> destination result type
     */
    protected <M1 extends AbstractNeuronEntity,
               T1 extends AbstractNeuronEntity,
               R1 extends AbstractMatchEntity<M1, T1>> void safeFieldsCopyFrom(R1 that) {
        this.mirrored = that.isMirrored();
        this.matchComputeFiles.clear();
        this.matchComputeFiles.putAll(that.getMatchComputeFiles());
    }

    public abstract AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity> duplicate(
            MatchCopier<AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>, AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> copier);

    public abstract AbstractMatchedTarget<? extends AbstractNeuronMetadata> metadata();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AbstractMatchEntity<?, ?> that = (AbstractMatchEntity<?, ?>) o;

        return new EqualsBuilder()
                .append(maskImage, that.maskImage)
                .append(matchedImage, that.matchedImage)
                .append(mirrored, that.mirrored)
                .append(matchComputeFiles, that.matchComputeFiles)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(maskImage)
                .append(matchedImage)
                .append(mirrored)
                .append(matchComputeFiles)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("entityId", getEntityId())
                .append("maskImage", maskImage != null ? maskImage.getMipId() : "<null>")
                .append("matchedImage", matchedImage != null ? matchedImage.getMipId() : "<null>")
                .append("mirrored", mirrored)
                .append("matchComputeFiles", matchComputeFiles)
                .toString();
    }
}
