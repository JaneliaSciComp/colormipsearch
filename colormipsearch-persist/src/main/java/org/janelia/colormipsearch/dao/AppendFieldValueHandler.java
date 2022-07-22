package org.janelia.colormipsearch.dao;

/**
 * @param <T> field value type
 */
public class AppendFieldValueHandler<T> extends AbstractEntityFieldValueHandler<T> {
    public AppendFieldValueHandler(T fieldValue) {
        super(fieldValue);
    }
}
