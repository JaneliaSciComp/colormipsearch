package org.janelia.colormipsearch.dataio;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class DataSourceParam {
    private String alignmentSpace;
    private Collection<String> libraries = new HashBag<>();
    private Collection<String> mipIDs = new HashSet<>();
    private Collection<String> names = new HashSet<>();
    private Collection<String> tags = new HashSet<>();
    private Collection<String> excludedTags = new HashSet<>();
    private Collection<String> datasets = new HashSet<>();
    private Collection<String> annotations = new HashSet<>();
    private Collection<String> excludedAnnotations = new HashSet<>();
    private long offset;
    private int size;

    public String getAlignmentSpace() {
        return alignmentSpace;
    }

    public DataSourceParam setAlignmentSpace(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
        return this;
    }

    public Collection<String> getLibraries() {
        return libraries;
    }

    public DataSourceParam addLibrary(String libraryName) {
        if (StringUtils.isNotBlank(libraryName)) this.libraries.add(libraryName);
        return this;
    }

    public DataSourceParam addLibraries(Collection<String> libraries) {
        if (libraries != null) libraries.forEach(this::addLibrary);
        return this;
    }

    public DataSourceParam resetLibraries() {
        libraries.clear();
        return this;
    }

    public Collection<String> getNames() {
        return names;
    }

    public DataSourceParam addName(String name) {
        if (StringUtils.isNotBlank(name)) this.names.add(name);
        return this;
    }

    public DataSourceParam addNames(Collection<String> names) {
        if (names != null) names.forEach(this::addName);
        return this;
    }

    public DataSourceParam resetNames() {
        names.clear();
        return this;
    }

    public Collection<String> getMipIDs() {
        return mipIDs;
    }

    public DataSourceParam addMipID(String mipID) {
        if (StringUtils.isNotBlank(mipID)) this.mipIDs.add(mipID);
        return this;
    }

    public DataSourceParam addMipIDs(Collection<String> mipIDs) {
        if (mipIDs != null) mipIDs.forEach(this::addMipID);
        return this;
    }

    public DataSourceParam resetMipIDs() {
        mipIDs.clear();
        return this;
    }

    public Collection<String> getTags() {
        return tags;
    }

    public DataSourceParam addTags(Collection<String> tags) {
        if (tags != null) tags.stream().filter(StringUtils::isNotBlank).forEach(t -> this.tags.add(t));
        return this;
    }

    public Collection<String> getExcludedTags() {
        return excludedTags;
    }

    public DataSourceParam addExcludedTags(Collection<String> excludedTags) {
        if (excludedTags != null) excludedTags.stream().filter(StringUtils::isNotBlank).forEach(t -> this.excludedTags.add(t));
        return this;
    }

    public Collection<String> getDatasets() {
        return datasets;
    }

    public DataSourceParam addDatasets(Collection<String> datasets) {
        if (datasets != null) datasets.stream().filter(StringUtils::isNotBlank).forEach(ds -> this.datasets.add(ds));
        return this;
    }

    public Collection<String> getAnnotations() {
        return annotations;
    }

    public DataSourceParam addAnnotations(Collection<String> annotations) {
        if (annotations != null) annotations.stream().filter(StringUtils::isNotBlank).forEach(a -> this.annotations.add(a));
        return this;
    }

    public Collection<String> getExcludedAnnotations() {
        return excludedAnnotations;
    }

    public DataSourceParam addExcludedAnnotations(Collection<String> annotations) {
        if (annotations != null) annotations.stream().filter(StringUtils::isNotBlank).forEach(a -> this.excludedAnnotations.add(a));
        return this;
    }

    public long getOffset() {
        return offset > 0 ? offset : 0;
    }

    public DataSourceParam setOffset(long offset) {
        this.offset = offset;
        return this;
    }

    public boolean hasOffset() {
        return offset > 0;
    }

    public int getSize() {
        return size;
    }

    public DataSourceParam setSize(int size) {
        this.size = size;
        return this;
    }

    public boolean hasSize() {
        return size > 0;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("alignmentSpace", alignmentSpace);
        params.put("libraries", libraries);
        params.put("mipIDs", mipIDs);
        params.put("names", names);
        params.put("tags", tags);
        params.put("excludedTags", excludedTags);
        params.put("datasets", datasets);
        params.put("annotations", annotations);
        params.put("excludedAnnotations", excludedAnnotations);
        params.put("offset", hasOffset() ? offset : null);
        params.put("size", hasSize() ? size : null);
        return params;
    }

    public DataSourceParam duplicate() {
        return new DataSourceParam()
                .setAlignmentSpace(alignmentSpace)
                .addLibraries(libraries)
                .addMipIDs(mipIDs)
                .addNames(names)
                .addTags(tags)
                .addExcludedTags(excludedTags)
                .addDatasets(datasets)
                .addAnnotations(annotations)
                .addExcludedAnnotations(excludedAnnotations)
                .setOffset(offset)
                .setSize(size)
                ;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DataSourceParam that = (DataSourceParam) o;

        return new EqualsBuilder()
                .append(alignmentSpace, that.alignmentSpace)
                .append(libraries, that.libraries)
                .append(mipIDs, that.mipIDs)
                .append(names, that.names)
                .append(tags, that.tags)
                .append(datasets, that.datasets)
                .append(offset, that.offset)
                .append(size, that.size)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(alignmentSpace)
                .append(libraries)
                .append(mipIDs)
                .append(names)
                .append(tags)
                .append(offset)
                .append(size)
                .toHashCode();
    }

    @Override
    public String toString() {
        StringBuilder sbuilder = new StringBuilder();
        if (StringUtils.isNotBlank(alignmentSpace)) {
            sbuilder.append(alignmentSpace).append('/');
        }
        if (!libraries.isEmpty()) {
            sbuilder.append(String.join(",", libraries));
        }
        if (hasOffset() || hasSize()) {
            sbuilder.append(':').append(getOffset());
        }
        if (hasSize()) {
            sbuilder.append(':').append(getSize());
        }
        return sbuilder.toString();
    }
}
