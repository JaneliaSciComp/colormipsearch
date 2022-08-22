package org.janelia.colormipsearch.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class NeuronSelector {
    private String neuronClassname; // full class name
    private String alignmentSpace; // alignment space
    private String libraryName; // library name
    private final Set<String> names = new HashSet<>(); // matching published names
    private final Set<String> mipIDs = new HashSet<>(); // matching MIP IDs
    private final Set<Number> entityIds = new HashSet<>(); // matching internal entity IDs
    private final Set<String> tags = new HashSet<>(); // matching tags

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

    public String getLibraryName() {
        return libraryName;
    }

    public NeuronSelector setLibraryName(String libraryName) {
        this.libraryName = libraryName;
        return this;
    }

    public boolean hasLibraryName() {
        return StringUtils.isNotBlank(libraryName);
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
        mipIDs.forEach(this::addMipID);
        return this;
    }

    public boolean hasMipIDs() {
        return CollectionUtils.isNotEmpty(mipIDs);
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

    public boolean isEmpty() {
        return !hasLibraryName()
                && !hasNames()
                && !hasMipIDs()
                && !hasEntityIds()
                && !hasTags();
    }

}
