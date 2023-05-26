package org.janelia.colormipsearch.api;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api.pppsearch.PPPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

    public static <T> void processPartitionStream(Stream<T> stream,
                                                  int partitionSize,
                                                  Consumer<List<T>> partitionHandler) {
        if (partitionSize == 1) {
            // trivial cause because the other one gets messed up
            // it's here only for completion purpose
            stream.map(Collections::singletonList).forEach(partitionHandler);
        } else {
            AtomicReference<List<T>> currentPartitionHolder = new AtomicReference<>(Collections.emptyList());
            Stream<List<T>> streamOfPartitions = stream.flatMap(e -> {
                List<T> l = currentPartitionHolder.accumulateAndGet(Collections.singletonList(e), (l1, l2) -> {
                    if (l1.size() == partitionSize) {
                        return l2;
                    } else {
                        List<T> updatedList = new ArrayList<>(l1);
                        updatedList.addAll(l2);
                        return updatedList;
                    }
                });
                return l.size() == partitionSize ? Stream.of(l) : Stream.empty();
            });
            if (stream.isParallel()) {
                streamOfPartitions.parallel().forEach(partitionHandler);
            } else {
                streamOfPartitions.forEach(partitionHandler);
            }
            List<T> leftContent = currentPartitionHolder.get();
            if (leftContent.size() > 0 && leftContent.size() < partitionSize) {
                partitionHandler.accept(leftContent);
            }
        }
    }

    public static <T> Collection<List<T>> partitionCollection(Collection<T> l, int partitionSizeArg) {
        final AtomicInteger index = new AtomicInteger();
        int partitionSize = partitionSizeArg > 0 ? partitionSizeArg : 1;
        return new ArrayList<>(l.stream()
                .collect(Collectors.groupingBy(docId -> index.getAndIncrement() / partitionSize, Collectors.toList()))
                .values());
    }

    public static <T> List<ScoredEntry<List<T>>> pickBestMatches(List<T> l,
                                                                 Function<T, String> groupingCriteria,
                                                                 Function<T, Number> scoreExtractor,
                                                                 int topResults,
                                                                 int limitSubResults) {
        Comparator<T> csrComparison = Comparator.comparing(scoreExtractor.andThen(n -> n.doubleValue()));
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

    public static <T, R extends Results<List<T>>> void writeResultsToJSONFile(R results, File f, ObjectWriter objectWriter) {
        try {
            if (CollectionUtils.isNotEmpty(results.getResults())) {
                if (f == null) {
                    objectWriter.writeValue(System.out, results);
                } else {
                    LOG.info("Writing {}", f);
                    objectWriter.writeValue(f, results);
                }
            }
        } catch (IOException e) {
            LOG.error("Error writing CDS results to json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

}
