package org.janelia.colormipsearch.dao.support;

/**
 * @param <T> field value type
 */
public class SetFieldValueHandler<T> extends AbstractEntityFieldValueHandler<T> {
    public SetFieldValueHandler(T fieldValue) {
        super(fieldValue);
    }
}
