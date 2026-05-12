package org.janelia.colormipsearch.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.model.GroupedItems;

public class ItemsHandling {

    /**
     * @param items                to be grouped by a certain criteria specified by @groupingCriteria@
     * @param toGroupingCriteria   criteria used for grouping @items@
     * @param fromGroupingCriteria extract element from grouping criteria
     * @param filter               filter for grouped elements
     * @param finalRankComparator  comparator used for ranking grouped items
     * @param groupFactory         factory for creating an object to hold items from a group
     * @param <E1>                 type of the input elements that need to be grouped together
     * @param <E2>                 type of the output elements that need to be grouped together
     * @param <K>                  type of the key used to group the elements
     * @param <G>                  result group type
     * @return
     */
    public static <E1, E2, K, G extends GroupedItems<K, E2>>
    List<G> groupItems(List<E1> items,
                       Function<E1, GroupingCriteria<E2, K>> toGroupingCriteria,
                       Function<GroupingCriteria<E2, K>, E2> fromGroupingCriteria,
                       Predicate<E2> filter,
                       @Nullable Comparator<E2> finalRankComparator,
                       Supplier<G> groupFactory) {
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(
                    items.stream()
                            .map(toGroupingCriteria)
                            .collect(Collectors.groupingBy(
                                    item -> item,
                                    Collectors.collectingAndThen(
                                            Collectors.toList(),
                                            sameKeyItems -> {
                                                G groupedItems = groupFactory.get();
                                                // get the key from the first item
                                                // the grouped items should always have at least one element
                                                groupedItems.setKey(sameKeyItems.get(0).getKey());
                                                List<E2> groupElements = sameKeyItems.stream()
                                                        .map(fromGroupingCriteria)
                                                        .filter(filter)
                                                        .collect(Collectors.toList());
                                                if (finalRankComparator != null) {
                                                    groupElements.sort(finalRankComparator);
                                                }
                                                groupedItems.setItems(groupElements);
                                                return groupedItems;
                                            }
                                    )))
                            .values())
                    ;
        }
    }

    public static <T> Map<Integer, List<T>> partitionCollection(Collection<T> l, int partitionSizeArg) {
        final AtomicInteger index = new AtomicInteger();
        int partitionSize = partitionSizeArg > 0 ? partitionSizeArg : 1;
        return l.stream()
                .collect(Collectors.groupingBy(docId -> index.getAndIncrement() / partitionSize));
    }

    public static <T> List<ScoredEntry<List<T>>> selectTopRankedElements(List<T> l,
                                                                         Function<T, String> groupingCriteria,
                                                                         Function<T, Number> scoreExtractor,
                                                                         int topResults,
                                                                         int limitSubResults) {
        Comparator<T> csrComparison = Comparator.comparing(scoreExtractor.andThen(Number::doubleValue));
        Map<String, List<T>> groupedResults = l.stream()
                .collect(Collectors.groupingBy(
                        val -> StringUtils.defaultIfBlank(groupingCriteria.apply(val), "UNKNOWN"),
                        Collectors.collectingAndThen(Collectors.toList(), r -> {
                            r.sort(csrComparison.reversed());
                            if (limitSubResults > 0 && limitSubResults < r.size()) {
                                return r.subList(0, limitSubResults);
                            } else {
                                return r;
                            }
                        })));
        List<ScoredEntry<List<T>>> bestResultsForSpecifiedCriteria = groupedResults.entrySet().stream()
                .map(e -> {
                    T maxValue = Collections.max(e.getValue(), csrComparison);
                    return new ScoredEntry<>(e.getKey(), scoreExtractor.apply(maxValue), e.getValue());
                })
                .sorted((se1, se2) -> Double.compare(se2.getScore().doubleValue(), se1.getScore().doubleValue())) // sort in reverse order
                .collect(Collectors.toList());
        if (topResults > 0 && bestResultsForSpecifiedCriteria.size() > topResults) {
            return bestResultsForSpecifiedCriteria.subList(0, topResults);
        } else {
            return bestResultsForSpecifiedCriteria;
        }
    }

}
