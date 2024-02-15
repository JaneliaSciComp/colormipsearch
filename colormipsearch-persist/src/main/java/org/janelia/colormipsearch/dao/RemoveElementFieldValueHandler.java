package org.janelia.colormipsearch.dao;

/**
 * @param <T> field value type
 */
public class RemoveElementFieldValueHandler<T> extends AbstractEntityFieldValueHandler<T> {
    public RemoveElementFieldValueHandler(T fieldValue) {
        super(fieldValue);
    }
}
