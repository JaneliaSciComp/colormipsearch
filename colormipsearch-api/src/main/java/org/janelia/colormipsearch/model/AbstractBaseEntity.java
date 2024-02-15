package org.janelia.colormipsearch.model;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.colormipsearch.model.annotations.EntityId;

/**
 * Common attributes for the database persisted data.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class AbstractBaseEntity implements BaseEntity {

    @EntityId
    private Number entityId;
    private Date createdDate = new Date();
    private Date updatedDate = new Date();
    // tags associated with the current entity. These are mainly used for versioning the data.
    private Set<String> tags = new HashSet<>();

    @Override
    public Number getEntityId() {
        return entityId;
    }

    @Override
    public void setEntityId(Number entityId) {
        this.entityId = entityId;
    }

    @Override
    public boolean hasEntityId() {
        return entityId != null;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public void addTag(String tag) {
        if (StringUtils.isNotBlank(tag))
            this.tags.add(tag.trim());
    }

    public void addAllTags(Collection<String> tags) {
        if (tags != null) {
            tags.forEach(this::addTag);
        }
    }

    public void removeTag(String tag) {
        if (StringUtils.isNotBlank(tag))
            this.tags.remove(tag.trim());
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AbstractBaseEntity that = (AbstractBaseEntity) o;

        return new EqualsBuilder().append(entityId, that.entityId).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(entityId).toHashCode();
    }
}
