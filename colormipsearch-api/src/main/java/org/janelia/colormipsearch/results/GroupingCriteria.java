package org.janelia.colormipsearch.results;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class GroupingCriteria<T, K> {

    static class WrappedKey<K1> {
        private final K1 key;
        private final List<Function<K1, ?>> keyFieldsUsedForEquality;

        private WrappedKey(K1 key, List<Function<K1, ?>> keyFieldsUsedForEquality) {
            this.key = key;
            this.keyFieldsUsedForEquality = keyFieldsUsedForEquality;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            @SuppressWarnings("unchecked")
            WrappedKey<K1> that = (WrappedKey<K1>) o;

            EqualsBuilder eqBuilder = new EqualsBuilder();
            for (Function<K1, ?> keyFieldSelector : keyFieldsUsedForEquality) {
                eqBuilder.append(keyFieldSelector.apply(this.key), keyFieldSelector.apply(that.key));
            }
            return eqBuilder.isEquals();
        }

        @Override
        public int hashCode() {
            HashCodeBuilder hashCodeBuilder = new HashCodeBuilder(17, 37);
            for (Function<K1, ?> keyFieldSelector : keyFieldsUsedForEquality) {
                hashCodeBuilder.append(keyFieldSelector.apply(this.key));
            }
            return hashCodeBuilder.toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append(key)
                    .toString();
        }
    }

    private final T item;
    private final WrappedKey<K> wrappedKey;

    public GroupingCriteria(T item,
                            Function<T, K> keySelector,
                            List<Function<K, ?>> keyFieldsUsedForEquality) {
        this.item = item;
        this.wrappedKey = new WrappedKey<>(keySelector.apply(item), keyFieldsUsedForEquality);
    }

    public T getItem() {
        return item;
    }

    public K getKey() {
        return wrappedKey.key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        GroupingCriteria<?, ?> that = (GroupingCriteria<?, ?>) o;
        return this.wrappedKey.equals(that.wrappedKey);
    }

    @Override
    public int hashCode() {
        return wrappedKey.hashCode();
    }
}
