package org.janelia.colormipsearch.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class NeuronSelector {
    private String neuronClassname; // full class name
    private String alignmentSpace; // alignment space
    private boolean checkIfNameValid = false;
    private final Set<String> libraries = new HashSet<>(); // library names
    private final Set<String> names = new HashSet<>(); // matching published names
    private final Set<String> mipIDs = new HashSet<>(); // matching MIP IDs
    private final Set<String> sourceRefIds = new HashSet<>(); // matching source Sample or Body IDs
    private final Set<String> datasetLabels = new HashSet<>(); // matching source Sample or Body IDs
    private final Set<Number> entityIds = new HashSet<>(); // matching internal entity IDs
    private final Set<String> tags = new HashSet<>(); // matching tags
    private final Set<String> excludedTags = new HashSet<>();
    private final Set<String> annotations = new HashSet<>(); // matching annotations
    private final Set<String> excludedAnnotations = new HashSet<>();
    private final List<Map<String, Set<String>>> processedTagsSelections = new ArrayList<>();
    public String getNeuronClassname() {
        return neuronClassname;
    }

    public NeuronSelector setNeuronClassname(String neuronClassname) {
        this.neuronClassname = neuronClassname;
        return this;
    }

    public boolean hasNeuronClassname() {
        return StringUtils.isNotBlank(neuronClassname);
    }

    public String getAlignmentSpace() {
        return alignmentSpace;
    }

    public NeuronSelector setAlignmentSpace(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
        return this;
    }

    public boolean hasAlignmentSpace() {
        return StringUtils.isNotBlank(alignmentSpace);
    }

    public Set<String> getLibraries() {
        return libraries;
    }

    public NeuronSelector addLibrary(String libraryName) {
        if (StringUtils.isNotBlank(libraryName)) this.libraries.add(libraryName);
        return this;
    }

    public NeuronSelector addLibraries(Collection<String> libraries) {
        if (libraries != null) libraries.forEach(this::addLibrary);
        return this;
    }

    public boolean hasLibraries() {
        return CollectionUtils.isNotEmpty(libraries);
    }

    public NeuronSelector withValidPubishingName() {
        this.checkIfNameValid = true;
        return this;
    }

    public boolean isCheckIfNameValid() {
        return checkIfNameValid;
    }

    public Set<String> getNames() {
        return names;
    }

    public NeuronSelector addName(String name) {
        if (StringUtils.isNotBlank(name)) this.names.add(name);
        return this;
    }

    public NeuronSelector addNames(Collection<String> names) {
        if (names != null) names.forEach(this::addName);
        return this;
    }

    public boolean hasNames() {
        return CollectionUtils.isNotEmpty(names);
    }

    public Set<String> getMipIDs() {
        return mipIDs;
    }

    public NeuronSelector addMipID(String mipID) {
        if (StringUtils.isNotBlank(mipID)) this.mipIDs.add(mipID);
        return this;
    }

    public NeuronSelector addMipIDs(Collection<String> mipIDs) {
        if (mipIDs != null) mipIDs.forEach(this::addMipID);
        return this;
    }

    public boolean hasMipIDs() {
        return CollectionUtils.isNotEmpty(mipIDs);
    }

    public Set<String> getSourceRefIds() {
        return sourceRefIds;
    }

    public NeuronSelector addSourceRefId(String sourceRefId) {
        if (StringUtils.isNotBlank(sourceRefId)) this.sourceRefIds.add(sourceRefId);
        return this;
    }

    public NeuronSelector addSourceRefIds(Collection<String> sourceRefIds) {
        if (sourceRefIds != null) sourceRefIds.forEach(this::addSourceRefId);
        return this;
    }

    public boolean hasSourceRefIds() {
        return CollectionUtils.isNotEmpty(sourceRefIds);
    }

    public Set<String> getDatasetLabels() {
        return datasetLabels;
    }

    public NeuronSelector addDatasetLabel(String datasetLabel) {
        if (StringUtils.isNotBlank(datasetLabel)) this.datasetLabels.add(datasetLabel);
        return this;
    }

    public NeuronSelector addDatasetLabels(Collection<String> datasetLabels) {
        if (datasetLabels != null) datasetLabels.forEach(this::addDatasetLabel);
        return this;
    }

    public boolean hasDatasetLabels() {
        return CollectionUtils.isNotEmpty(datasetLabels);
    }

    public Set<Number> getEntityIds() {
        return entityIds;
    }

    public NeuronSelector addEntityId(Number entityId) {
        if (entityId != null) this.entityIds.add(entityId);
        return this;
    }

    public NeuronSelector addEntityIds(Collection<Number> entityIds) {
        entityIds.forEach(this::addEntityId);
        return this;
    }

    public boolean hasEntityIds() {
        return CollectionUtils.isNotEmpty(entityIds);
    }

    public Set<String> getTags() {
        return tags;
    }

    public NeuronSelector addTag(String tag) {
        if (StringUtils.isNotBlank(tag)) this.tags.add(tag);
        return this;
    }

    public NeuronSelector addTags(Collection<String> tags) {
        if (tags != null) tags.forEach(this::addTag);
        return this;
    }

    public boolean hasTags() {
        return CollectionUtils.isNotEmpty(tags);
    }

    public Set<String> getExcludedTags() {
        return excludedTags;
    }

    public NeuronSelector addExcludedTag(String tag) {
        if (StringUtils.isNotBlank(tag)) this.excludedTags.add(tag);
        return this;
    }

    public NeuronSelector addExcludedTags(Collection<String> tags) {
        if (tags != null) tags.forEach(this::addExcludedTag);
        return this;
    }

    public boolean hasExcludedTags() {
        return CollectionUtils.isNotEmpty(excludedTags);
    }

    public Set<String> getAnnotations() {
        return annotations;
    }

    public NeuronSelector addAnnotation(String annotation) {
        if (StringUtils.isNotBlank(annotation)) this.annotations.add(annotation);
        return this;
    }

    public NeuronSelector addAnnotations(Collection<String> annotations) {
        if (annotations != null) annotations.forEach(this::addAnnotation);
        return this;
    }

    public boolean hasAnnotations() {
        return CollectionUtils.isNotEmpty(annotations);
    }

    public Set<String> getExcludedAnnotations() {
        return excludedAnnotations;
    }

    public NeuronSelector addExcludedAnnotation(String annotation) {
        if (StringUtils.isNotBlank(annotation)) this.excludedAnnotations.add(annotation);
        return this;
    }

    public NeuronSelector addExcludedAnnotations(Collection<String> annotations) {
        if (annotations != null) annotations.forEach(this::addExcludedAnnotation);
        return this;
    }

    public boolean hasExcludedAnnotations() {
        return CollectionUtils.isNotEmpty(excludedAnnotations);
    }

    public List<Map<String, Set<String>>> getProcessedTagsSelections() {
        return processedTagsSelections;
    }

    public NeuronSelector addNewProcessedTagSelection(String processingType, String tag) {
        if (StringUtils.isNotBlank(processingType) && StringUtils.isNotBlank(tag)) {
            processedTagsSelections.add(new HashMap<>());
            addProcessedTag(processingType, tag);
        }
        return this;
    }

    public NeuronSelector addProcessedTag(String processingType, String tag) {
        if (StringUtils.isNotBlank(processingType) && StringUtils.isNotBlank(tag)) {
            if (processedTagsSelections.isEmpty()) {
                processedTagsSelections.add(new HashMap<>());
            }
            Map<String, Set<String>> currentProcessedTagsSelection = processedTagsSelections.get(processedTagsSelections.size()-1);
            Set<String> processingTags;
            if (CollectionUtils.isEmpty(currentProcessedTagsSelection.get(processingType))) {
                processingTags = new HashSet<>();
                currentProcessedTagsSelection.put(processingType, processingTags);
            } else {
                processingTags = currentProcessedTagsSelection.get(processingType);
            }
            processingTags.add(tag);
        }
        return this;
    }

    public NeuronSelector addNewProcessedTagsSelection(String processingType, Collection<String> tags) {
        Set<String> nonEmptyTags = tags.stream().filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        if (StringUtils.isNotBlank(processingType) && CollectionUtils.isNotEmpty(nonEmptyTags)) {
            processedTagsSelections.add(new HashMap<>());
            addProcessedTags(processingType, nonEmptyTags);
        }
        return this;
    }

    public NeuronSelector addProcessedTags(String processingType, Collection<String> tags) {
        Set<String> nonEmptyTags = tags.stream().filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        if (StringUtils.isNotBlank(processingType) && CollectionUtils.isNotEmpty(nonEmptyTags)) {
            if (processedTagsSelections.isEmpty()) {
                processedTagsSelections.add(new HashMap<>());
            }
            Map<String, Set<String>> currentProcessedTagsSelection = processedTagsSelections.get(processedTagsSelections.size()-1);
            Set<String> processingTags;
            if (CollectionUtils.isEmpty(currentProcessedTagsSelection.get(processingType))) {
                processingTags = new HashSet<>();
                currentProcessedTagsSelection.put(processingType, processingTags);
            } else {
                processingTags = currentProcessedTagsSelection.get(processingType);
            }
            processingTags.addAll(nonEmptyTags);
        }
        return this;
    }

    public boolean hasProcessedTags() {
        return !processedTagsSelections.isEmpty();
    }

    public boolean isEmpty() {
        return !hasLibraries()
                && !hasNames()
                && !hasMipIDs()
                && !hasEntityIds()
                && !hasTags()
                && !hasExcludedTags()
                && !hasDatasetLabels()
                && !hasSourceRefIds()
                && !hasProcessedTags()
                && !hasAnnotations()
                && !hasExcludedAnnotations()
                ;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }
}
