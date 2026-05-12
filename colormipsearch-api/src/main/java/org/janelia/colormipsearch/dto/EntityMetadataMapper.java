package org.janelia.colormipsearch.dto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;

public final class EntityMetadataMapper {
    private static final Pattern LM_REG_EX_PATTERN = Pattern.compile("(.+)_REG_UNISEX_(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECTIVE_PATTERN = Pattern.compile("\\d+x", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_OBJECTIVE = "40x";

    private EntityMetadataMapper() {
    }

    public static AbstractNeuronMetadata toMetadata(AbstractNeuronEntity entity) {
        if (entity == null) {
            return null;
        } else if (entity instanceof EMNeuronEntity) {
            return toMetadata((EMNeuronEntity) entity);
        } else if (entity instanceof LMNeuronEntity) {
            return toMetadata((LMNeuronEntity) entity);
        } else {
            throw new IllegalArgumentException("Unsupported neuron entity type: " + entity.getClass().getName());
        }
    }

    public static EMNeuronMetadata toMetadata(EMNeuronEntity entity) {
        EMNeuronMetadata n = new EMNeuronMetadata();
        populateCommonNeuronMetadata(entity, n);
        n.setEmRefId(entity.getSourceRefIdOnly());
        n.setNeuronType(entity.getNeuronType());
        n.setNeuronInstance(entity.getNeuronInstance());
        return n;
    }

    public static LMNeuronMetadata toMetadata(LMNeuronEntity entity) {
        LMNeuronMetadata n = new LMNeuronMetadata();
        populateCommonNeuronMetadata(entity, n);
        n.setSlideCode(entity.getSlideCode());
        n.setAnatomicalArea(entity.getAnatomicalArea());
        n.setGender(entity.getGender());
        n.setObjective(entity.getObjective());
        return n;
    }

    public static AbstractMatchedTarget<? extends AbstractNeuronMetadata> toMatchedTarget(
            AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity> entity) {
        if (entity == null) {
            return null;
        } else if (entity instanceof CDMatchEntity) {
            return toMatchedTarget((CDMatchEntity<?, ?>) entity);
        } else if (entity instanceof PPPMatchEntity) {
            return toMatchedTarget((PPPMatchEntity<?, ?>) entity);
        } else {
            throw new IllegalArgumentException("Unsupported match entity type: " + entity.getClass().getName());
        }
    }

    public static CDMatchedTarget<? extends AbstractNeuronMetadata> toMatchedTarget(CDMatchEntity<?, ?> entity) {
        CDMatchedTarget<AbstractNeuronMetadata> m = new CDMatchedTarget<>();
        m.setMatchInternalId(entity.getEntityId());
        m.setMirrored(entity.isMirrored());
        m.setNormalizedScore(entity.getNormalizedScore());
        m.setMatchingPixels(entity.getMatchingPixels());
        return m;
    }

    public static PPPMatchedTarget<? extends AbstractNeuronMetadata> toMatchedTarget(PPPMatchEntity<?, ?> entity) {
        PPPMatchedTarget<AbstractNeuronMetadata> m = new PPPMatchedTarget<>();
        m.setMatchInternalId(entity.getEntityId());
        AbstractNeuronEntity matchedImage = entity.getMatchedImage();
        if (matchedImage != null) {
            m.setTargetImage(toMetadata(matchedImage));
        }
        updateLMSampleInfo(entity, m);
        if (entity.hasSourceImageFiles()) {
            m.addSourceImageFileTypes(entity.getSourceImageFiles().keySet());
        }
        m.setMirrored(entity.isMirrored());
        m.setRank(entity.getRank());
        m.setScore((int) Math.abs(entity.getCoverageScore()));
        return m;
    }

    private static void populateCommonNeuronMetadata(AbstractNeuronEntity entity, AbstractNeuronMetadata n) {
        n.setInternalId(entity.getEntityId());
        n.setAlignmentSpace(entity.getAlignmentSpace());
        n.setMipId(entity.getMipId());
        n.setLibraryName(entity.getLibraryName());
        n.setPublishedName(entity.getPublishedName());
        n.setAnnotations(entity.getNeuronTerms());
        entity.getComputeFiles().forEach((ft, fd) -> n.setNeuronComputeFile(ft, fd.getFileName()));
        entity.getProcessedTags().forEach(n::putProcessedTags);
    }

    private static void updateLMSampleInfo(PPPMatchEntity<?, ?> entity, PPPMatchedTarget<AbstractNeuronMetadata> m) {
        m.setSourceLmLibrary(entity.getSourceLmLibrary());
        Matcher matcher = LM_REG_EX_PATTERN.matcher(entity.getSourceLmName());
        if (matcher.find()) {
            m.setSourceLmName(matcher.group(1));
            String objectiveCandidate = matcher.group(2);
            if (OBJECTIVE_PATTERN.matcher(objectiveCandidate).find()) {
                m.setSourceObjective(objectiveCandidate);
            } else {
                m.setSourceObjective(DEFAULT_OBJECTIVE);
            }
        } else {
            m.setSourceLmName(entity.getSourceLmName());
            m.setSourceObjective(DEFAULT_OBJECTIVE);
        }
    }
}
