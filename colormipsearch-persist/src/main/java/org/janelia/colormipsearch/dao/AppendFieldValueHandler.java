package org.janelia.colormipsearch.dao;

/**
 * @param <T> field value type
 */
public class AppendFieldValueHandler<T> extends AbstractEntityFieldValueHandler<T> {
    private final boolean addToSet;

    public AppendFieldValueHandler(T fieldValue, boolean addToSet) {
        super(fieldValue);
        this.addToSet = addToSet;
    }

    public boolean isAddToSet() {
        return addToSet;
    }
}
