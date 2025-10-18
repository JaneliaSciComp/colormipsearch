package org.janelia.colormipsearch.model;

public class EntityField<V> {

    public enum FieldOp {
        APPEND_TO_LIST,
        ADD_TO_SET,
        SET,
        UNSET
    };

    private final String fieldName;
    private final V value;
    private final FieldOp op;

    public EntityField(String fieldName, V value) {
        this(fieldName, value, FieldOp.SET);
    }

    public EntityField(String fieldName, V value, FieldOp op) {
        this.fieldName = fieldName;
        this.value = value;
        this.op = op;
    }

    public String getFieldName() {
        return fieldName;
    }

    public FieldOp getOp() {
        return op;
    }

    public V getValue() {
        return value;
    }
}
