package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageAccessUtilsTest {

    @Test
    public void getNeighbors() {
        class TestData {
            final int ndims;
            final int dist;
            final int expectedNeighbors;

            TestData(int ndims, int dist, int expectedNeighbors) {
                this.ndims = ndims;
                this.dist = dist;
                this.expectedNeighbors = expectedNeighbors;
            }
        }
        TestData[] testData = new TestData[] {
                new TestData(2, 1, 9),
                new TestData(2, 2, 25),
                new TestData(3, 1, 27),
                new TestData(3, 2, 125),
        };

        for (TestData td : testData) {
            List<long[]> resultsWithCurrentPos = ImageAccessUtils.streamNeighborsWithinDist(td.ndims, td.dist, true)
                    .collect(Collectors.toList());
            assertEquals (td.expectedNeighbors, resultsWithCurrentPos.size());
            List<long[]> resultsWithoutCurrentPos = ImageAccessUtils.streamNeighborsWithinDist(td.ndims, td.dist, false)
                    .collect(Collectors.toList());
            assertEquals (td.expectedNeighbors - 1, resultsWithoutCurrentPos.size());
        }
    }

    @Test
    public void getNextNeighbors() {
        class TestData {
            final int ndims;
            final int expectedNeighbors;

            TestData(int ndims, int expectedNeighbors) {
                this.ndims = ndims;
                this.expectedNeighbors = expectedNeighbors;
            }
        }
        TestData[] testData = new TestData[] {
                new TestData(2, 3),
                new TestData(3, 7),
                new TestData(4, 15),
                new TestData(5, 31)
        };

        for (TestData td : testData) {
            List<long[]> resultsWithCenter = ImageAccessUtils.streamNeighborsWithinDist(td.ndims, 1, true)
                    .filter(coords -> Arrays.stream(coords).allMatch(p -> p >= 0))
                    .collect(Collectors.toList());
            assertEquals (td.expectedNeighbors + 1, resultsWithCenter.size());
            List<long[]> resultsWithoutCenter = ImageAccessUtils.streamNeighborsWithinDist(td.ndims, 1, false)
                    .filter(coords -> Arrays.stream(coords).allMatch(p -> p >= 0))
                    .collect(Collectors.toList());
            assertEquals (td.expectedNeighbors, resultsWithoutCenter.size());
        }
    }

}
