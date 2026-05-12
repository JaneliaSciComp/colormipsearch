package org.janelia.colormipsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

@PersistenceInfo(storeName ="cdMatches", archiveName = "cdMatchesArchive")
public class CDMatchEntity<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> extends AbstractMatchEntity<M, T> {
    private Float normalizedScore;
    private Integer matchingPixels;
    private Float matchingPixelsRatio;
    private Long bidirectionalAreaGap;
    private Long gradientAreaGap;
    private Long highExpressionArea;
    private boolean matchFound;
    private String errors;

    public Float getNormalizedScore() {
        return normalizedScore;
    }

    void setNormalizedScore(Float normalizedScore) {
        this.normalizedScore = normalizedScore;
    }

    public boolean hasNormalizedScore() {
        return normalizedScore != null;
    }

    public Integer getMatchingPixels() {
        return matchingPixels;
    }

    public void setMatchingPixels(Integer matchingPixels) {
        this.matchingPixels = matchingPixels;
    }

    public Float getMatchingPixelsRatio() {
        return matchingPixelsRatio;
    }

    public void setMatchingPixelsRatio(Float matchingPixelsRatio) {
        this.matchingPixelsRatio = matchingPixelsRatio;
    }

    public Long getBidirectionalAreaGap() {
        return bidirectionalAreaGap;
    }

    public void setBidirectionalAreaGap(Long bidirectionalAreaGap) {
        this.bidirectionalAreaGap = bidirectionalAreaGap;
    }

    public Long getGradientAreaGap() {
        return gradientAreaGap;
    }

    public void setGradientAreaGap(Long gradientAreaGap) {
        this.gradientAreaGap = gradientAreaGap;
    }

    public Long getHighExpressionArea() {
        return highExpressionArea;
    }

    public void setHighExpressionArea(Long highExpressionArea) {
        this.highExpressionArea = highExpressionArea;
    }

    /**
     * This is the method to set the normalized score outside the persistence layer.
     * @param score
     */
    public void updateNormalizedScore(Float score) {
        this.normalizedScore = score;
    }

    public void resetGradientScores() {
        this.gradientAreaGap = null;
        this.highExpressionArea = null;
        this.bidirectionalAreaGap = null;
        this.normalizedScore = null;
    }

    @JsonIgnore
    public boolean isMatchFound() {
        return matchFound;
    }

    public void setMatchFound(boolean matchFound) {
        this.matchFound = matchFound;
    }

    @JsonIgnore
    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public boolean hasErrors() {
        return StringUtils.isNotBlank(errors);
    }

    public boolean hasNoErrors() {
        return !hasErrors();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity> duplicate(
            MatchCopier<AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>, AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> copier) {
        CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity> clone = new CDMatchEntity<>();
        // copy fields that are safe to copy
        clone.safeFieldsCopyFrom(this);
        // copy fields specific to this class
        clone.normalizedScore = this.normalizedScore;
        clone.matchingPixels = this.matchingPixels;
        clone.matchingPixelsRatio = this.matchingPixelsRatio;
        clone.bidirectionalAreaGap = this.bidirectionalAreaGap;
        clone.gradientAreaGap = this.gradientAreaGap;
        clone.highExpressionArea = this.highExpressionArea;
        clone.matchFound = this.matchFound;
        clone.errors = this.errors;
        // apply the copier
        copier.copy((AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>) this, clone);
        return clone;
    }

}
