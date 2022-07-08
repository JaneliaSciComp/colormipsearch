package org.janelia.colormipsearch.results;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @param <T> element types to be grouped
 * @param <K> key type derived from the element type
 */
public abstract class AbstractGroupedItems<T, K> {

    private K key;
    private List<T> items;

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    @JsonIgnore
    public int getItemsCount() {
        return items == null ? 0 : items.size();
    }
}
