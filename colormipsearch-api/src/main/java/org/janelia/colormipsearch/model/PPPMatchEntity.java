package org.janelia.colormipsearch.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.MapUtils;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.PPPMatchedTarget;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;

@PersistenceInfo(storeName ="pppMatches", archiveName = "pppMatchesArchive")
public class PPPMatchEntity<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> extends AbstractMatchEntity<M, T> {

    private static final Pattern LM_REG_EX_PATTERN = Pattern.compile("(.+)_REG_UNISEX_(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECTIVE_PATTERN = Pattern.compile("\\d+x", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_OBJECTIVE = "40x";
    private static final String UNKNOWN_ALIGNMENT_SPACE = "UNKNOWN-AS";

    private String sourceEmName;
    private String sourceEmLibrary;
    private String sourceLmName;
    private String sourceLmLibrary;
    private Double coverageScore;
    private Double aggregateCoverage;
    private Double rank;
    private String lmPublishedName;
    private String lmSlideCode;
    private String lmObjective;
    private String inputAlignmentSpace = UNKNOWN_ALIGNMENT_SPACE;
    private Map<PPPScreenshotType, String> sourceImageFiles;
    private List<PPPSkeletonMatch> skeletonMatches;

    public String getSourceEmLibrary() {
        return sourceEmLibrary;
    }

    public void setSourceEmLibrary(String sourceEmLibrary) {
        this.sourceEmLibrary = sourceEmLibrary;
    }

    public String getSourceEmName() {
        return sourceEmName;
    }

    public void setSourceEmName(String sourceEmName) {
        this.sourceEmName = sourceEmName;
    }

    public String getSourceLmName() {
        return sourceLmName;
    }

    public void setSourceLmName(String sourceLmName) {
        this.sourceLmName = sourceLmName;
    }

    public String getSourceLmLibrary() {
        return sourceLmLibrary;
    }

    public void setSourceLmLibrary(String sourceLmLibrary) {
        this.sourceLmLibrary = sourceLmLibrary;
    }

    public Double getCoverageScore() {
        return coverageScore;
    }

    public void setCoverageScore(Double coverageScore) {
        this.coverageScore = coverageScore;
    }

    public Double getAggregateCoverage() {
        return aggregateCoverage;
    }

    public void setAggregateCoverage(Double aggregateCoverage) {
        this.aggregateCoverage = aggregateCoverage;
    }

    public Double getRank() {
        return rank;
    }

    public void setRank(Double rank) {
        this.rank = rank;
    }

    public String getLmPublishedName() {
        return lmPublishedName;
    }

    public void setLmPublishedName(String lmPublishedName) {
        this.lmPublishedName = lmPublishedName;
    }

    public String getLmSlideCode() {
        return lmSlideCode;
    }

    public void setLmSlideCode(String lmSlideCode) {
        this.lmSlideCode = lmSlideCode;
    }

    public String getLmObjective() {
        return lmObjective;
    }

    public void setLmObjective(String lmObjective) {
        this.lmObjective = lmObjective;
    }

    public String getInputAlignmentSpace() {
        return inputAlignmentSpace;
    }

    public void setInputAlignmentSpace(String inputAlignmentSpace) {
        this.inputAlignmentSpace = inputAlignmentSpace;
    }

    public Map<PPPScreenshotType, String> getSourceImageFiles() {
        return sourceImageFiles;
    }

    public void setSourceImageFiles(Map<PPPScreenshotType, String> sourceImageFiles) {
        this.sourceImageFiles = sourceImageFiles;
    }

    public void addSourceImageFile(String imageName) {
        PPPScreenshotType imageFileType = PPPScreenshotType.findScreenshotType(imageName);
        if (imageFileType != null) {
            if (sourceImageFiles == null) {
                sourceImageFiles = new HashMap<>();
            }
            sourceImageFiles.put(imageFileType, imageName);
        }
    }

    public boolean hasSourceImageFiles() {
        return MapUtils.isNotEmpty(sourceImageFiles);
    }

    public List<PPPSkeletonMatch> getSkeletonMatches() {
        return skeletonMatches;
    }

    public void setSkeletonMatches(List<PPPSkeletonMatch> skeletonMatches) {
        this.skeletonMatches = skeletonMatches;
    }

    @SuppressWarnings("unchecked")
    @Override
    public PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity> duplicate(
            MatchCopier<AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>, AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> copier) {
        PPPMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity> clone = new PPPMatchEntity<>();
        // copy fields that are safe to copy
        clone.safeFieldsCopyFrom(this);
        // copy fields specific to this class
        clone.sourceEmName = this.sourceEmName;
        clone.sourceEmLibrary = this.sourceEmLibrary;
        clone.sourceLmName = this.sourceLmName;
        clone.sourceLmLibrary = this.sourceLmLibrary;
        clone.coverageScore = this.coverageScore;
        clone.aggregateCoverage = this.aggregateCoverage;
        clone.rank = this.rank;
        clone.sourceImageFiles = this.sourceImageFiles;
        clone.skeletonMatches = this.skeletonMatches;
        // apply the copier
        copier.copy((AbstractMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>) this, clone);
        return clone;
    }

    @Override
    public PPPMatchedTarget<? extends AbstractNeuronMetadata> metadata() {
        PPPMatchedTarget<AbstractNeuronMetadata> m = new PPPMatchedTarget<>();
        m.setMatchInternalId(getEntityId());
        T matchedImage = getMatchedImage();
        if (matchedImage != null) {
            AbstractNeuronMetadata n = getMatchedImage().metadata();
            m.setTargetImage(n);
        }
        updateLMSampleInfo(m);
        if (hasSourceImageFiles()) m.addSourceImageFileTypes(sourceImageFiles.keySet());
        m.setMirrored(isMirrored());
        m.setRank(getRank());
        m.setScore((int)Math.abs(coverageScore));
        return m;
    }

    public String extractLMSampleName() {
        Matcher matcher = LM_REG_EX_PATTERN.matcher(getSourceLmName());
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return getSourceLmName();
        }
    }

    private void updateLMSampleInfo(PPPMatchedTarget<AbstractNeuronMetadata> m) {
        m.setSourceLmLibrary(getSourceLmLibrary());
        Matcher matcher = LM_REG_EX_PATTERN.matcher(getSourceLmName());
        if (matcher.find()) {
            m.setSourceLmName(matcher.group(1));
            String objectiveCandidate = matcher.group(2);
            if (OBJECTIVE_PATTERN.matcher(objectiveCandidate).find()) {
                m.setSourceObjective(objectiveCandidate);
            } else {
                m.setSourceObjective(DEFAULT_OBJECTIVE);
            }
        } else {
            // set some default values
            m.setSourceLmName(getSourceLmName());
            m.setSourceObjective(DEFAULT_OBJECTIVE);
        }
    }

}
