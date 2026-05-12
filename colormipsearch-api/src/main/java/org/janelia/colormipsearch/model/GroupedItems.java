package org.janelia.colormipsearch.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param <K> key type
 * @param <T> type of grouped elements
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupedItems<K, T> {

    public static <K1, T1> GroupedItems<K1, T1> createGroupedItems(K1 k, Collection<T1> items) {
        GroupedItems<K1, T1> r = new GroupedItems<K1, T1>();
        r.setKey(k);
        r.setItems(new ArrayList<>(items));
        return r;
    }

    private K key;
    private List<T> items;

    @JsonProperty
    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    @JsonProperty("results")
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
