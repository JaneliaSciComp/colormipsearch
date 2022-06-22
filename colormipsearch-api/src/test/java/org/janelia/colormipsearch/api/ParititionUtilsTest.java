package org.janelia.colormipsearch.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ParititionUtilsTest {

    @Test
    public void partitionStream() {
        int[][] testData = new int[][] {
                { 100, 100},
                { 100, 25},
                { 101, 26},
                { 200, 2 },
                { 100, 1 },
                { 150, 200},
                { 200, 36 },
        };
        for (int[] td : testData) {
            int maxValue = td[0];
            int partitionSize = td[1];
            List<List<Integer>> listOfList = Collections.synchronizedList(new ArrayList<>());
            PartitionUtils.processPartitionStream(
                    IntStream.range(0, maxValue).boxed().parallel(),
                    partitionSize,
                    listOfList::add
            );

            int exactPartitionAdjustment = maxValue % partitionSize == 0 ? 0 : 1;
            int nPartitions = maxValue / partitionSize + exactPartitionAdjustment;
            assertEquals("Test: " + Arrays.toString(td), nPartitions, listOfList.size());
            for (int i = 0; i < nPartitions-1; i++) {
                assertEquals("Test: " + Arrays.toString(td) + ": partition: " + (i+1), partitionSize, listOfList.get(i).size());
            }
            assertEquals(
                    "Test: " + Arrays.toString(td),
                    exactPartitionAdjustment == 0 ? partitionSize : maxValue % partitionSize,
                    listOfList.get(nPartitions-1).size()
            );
            List<Integer> concatenatedList = listOfList.stream().flatMap(l -> l.stream()).collect(Collectors.toList());
            assertNotEquals(
                    "Test: " + Arrays.toString(td),
                    IntStream.range(0, maxValue).boxed().collect(Collectors.toList()),
                    concatenatedList);
            concatenatedList.sort(Comparator.naturalOrder());
            assertEquals(
                    "Test: " + Arrays.toString(td),
                    IntStream.range(0, maxValue).boxed().collect(Collectors.toList()),
                    concatenatedList);
        }
    }

}
