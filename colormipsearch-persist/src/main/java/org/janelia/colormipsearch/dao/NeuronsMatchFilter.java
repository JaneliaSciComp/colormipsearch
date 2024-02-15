package org.janelia.colormipsearch.dao;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public class NeuronsMatchFilter<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> {

    private ScoresFilter scoresFilter; // score filter
    private Class<?> matchEntityType;
    private Collection<Number> matchEntityIds; // match entity IDs
    private Collection<Number> maskEntityIds; // mask entity IDs
    private Collection<Number> targetEntityIds; // target entity IDs
    private final Set<String> tags = new HashSet<>(); // matching tags
    private final Set<String> excludedTags = new HashSet<>(); // tags that should not exist

    public ScoresFilter getScoresFilter() {
        return scoresFilter;
    }

    public NeuronsMatchFilter<R> setScoresFilter(ScoresFilter scoresFilter) {
        this.scoresFilter = scoresFilter;
        return this;
    }


    public Class<?> getMatchEntityType() {
        return matchEntityType;
    }

    public NeuronsMatchFilter<R> setMatchEntityType(Class<?> matchEntityType) {
        this.matchEntityType = matchEntityType;
        return this;
    }

    public Collection<Number> getMatchEntityIds() {
        return matchEntityIds;
    }

    public NeuronsMatchFilter<R> setMatchEntityIds(Collection<Number> matchEntityIds) {
        this.matchEntityIds = matchEntityIds;
        return this;
    }

    public Collection<Number> getMaskEntityIds() {
        return maskEntityIds;
    }

    public NeuronsMatchFilter<R> setMaskEntityIds(Collection<Number> maskEntityIds) {
        this.maskEntityIds = maskEntityIds;
        return this;
    }

    public Collection<Number> getTargetEntityIds() {
        return targetEntityIds;
    }

    public NeuronsMatchFilter<R> setTargetEntityIds(Collection<Number> targetEntityIds) {
        this.targetEntityIds = targetEntityIds;
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public NeuronsMatchFilter<R> addTag(String tag) {
        if (StringUtils.isNotBlank(tag)) this.tags.add(tag);
        return this;
    }

    public NeuronsMatchFilter<R> addTags(Collection<String> tags) {
        if (tags != null) tags.forEach(this::addTag);
        return this;
    }

    public boolean hasTags() {
        return CollectionUtils.isNotEmpty(tags);
    }

    public Set<String> getExcludedTags() {
        return excludedTags;
    }

    public NeuronsMatchFilter<R> addExcludedTag(String tag) {
        if (StringUtils.isNotBlank(tag)) this.excludedTags.add(tag);
        return this;
    }

    public NeuronsMatchFilter<R> addExcludedTags(Collection<String> tags) {
        if (tags != null) tags.forEach(this::addExcludedTag);
        return this;
    }

    public boolean hasExcludedTags() {
        return CollectionUtils.isNotEmpty(excludedTags);
    }

    public boolean isEmpty() {
        return (scoresFilter == null || scoresFilter.isEmpty())
                && CollectionUtils.isEmpty(maskEntityIds)
                && CollectionUtils.isEmpty(targetEntityIds)
                && !hasTags();
    }
}
