package org.janelia.colormipsearch.model;

public class EntityField<V> {
    private final String fieldName;
    private final boolean toBeAppended;
    private final boolean toBeRemoved;
    private final V value;

    public EntityField(String fieldName, boolean toBeAppended, V value) {
        this(fieldName, toBeAppended, false, value);
    }

    public EntityField(String fieldName, boolean toBeAppended, boolean toBeRemoved, V value) {
        this.fieldName = fieldName;
        this.toBeAppended = toBeAppended;
        this.toBeRemoved = toBeRemoved;
        this.value = value;
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isToBeAppended() {
        return toBeAppended;
    }

    public boolean isToBeRemoved() {
        return toBeRemoved;
    }

    public V getValue() {
        return value;
    }
}
