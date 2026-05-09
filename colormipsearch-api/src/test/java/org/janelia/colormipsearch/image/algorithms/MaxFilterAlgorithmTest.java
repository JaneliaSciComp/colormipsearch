package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.AbstractImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class MaxFilterAlgorithmTest {
    private static final Logger LOG = LoggerFactory.getLogger(MaxFilterAlgorithmTest.class);

    @Test
    public void maxGrayFilter3DMatchesReferenceForSingleCenterVoxel() {
        Gray16ImageArray input = new Gray16ImageArray(7, 6, 5);
        input.setPackedIntValAtCoords(3, 3, 2, 123);

        assertMatchesReference(input, 2, 2, 1);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceAtBoundaries() {
        Gray16ImageArray input = new Gray16ImageArray(7, 6, 5);
        input.setPackedIntValAtCoords(0, 0, 0, 25);
        input.setPackedIntValAtCoords(6, 5, 4, 530);
        input.setPackedIntValAtCoords(0, 5, 2, 80);
        input.setPackedIntValAtCoords(6, 0, 3, 140);

        assertMatchesReference(input, 2, 2, 1);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceForOverlappingVoxels() {
        Gray16ImageArray input = new Gray16ImageArray(8, 7, 5);
        input.setPackedIntValAtCoords(3, 3, 2, 90);
        input.setPackedIntValAtCoords(4, 3, 2, 220);
        input.setPackedIntValAtCoords(3, 4, 2, 65535);
        input.setPackedIntValAtCoords(5, 5, 3, 17);

        assertMatchesReference(input, 2, 2, 1);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceForEmptyVolume() {
        Gray16ImageArray input = new Gray16ImageArray(6, 5, 4);

        assertMatchesReference(input, 2, 2, 1);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceWhenZRadiusIsZero() {
        Gray16ImageArray input = new Gray16ImageArray(7, 6, 3);
        input.setPackedIntValAtCoords(2, 3, 0, 34);
        input.setPackedIntValAtCoords(5, 1, 1, 255);
        input.setPackedIntValAtCoords(0, 5, 2, 87);

        assertMatchesReference(input, 2, 2, 0);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceWhenAllRadiiAreZero() {
        Gray16ImageArray input = new Gray16ImageArray(5, 4, 3);
        input.setPackedIntValAtCoords(1, 2, 0, 11);
        input.setPackedIntValAtCoords(4, 3, 2, 4095);

        assertMatchesReference(input, 0, 0, 0);
    }

    @Test
    public void maxGrayFilter3DMatchesReferenceForValuesAbove16BitRange() {
        int width = 5;
        int height = 4;
        int depth = 3;
        int[] values = new int[width * height * depth];
        values[1 + 2 * width] = 70000;
        values[2 + 2 * width] = 60000;

        ImageArray input = new AbstractImageArray(width, height, depth, 1) {
            @Override
            public int getPackedIntValAtIndex(int pi) {
                return values[pi];
            }

            @Override
            public int getChannelIntValAtIndex(int pi, int ch) {
                return values[pi];
            }
        };

        assertMatchesReference(input, 1, 1, 1);
    }

    private void assertMatchesReference(ImageArray input, int rx, int ry, int rz) {
        long startReference = System.nanoTime();
        ImageArray reference = naiveGray16MaxFilter3D(input, rx, ry, rz);
        long endReference = System.nanoTime();
        ImageArray actual = MaxFilterAlgorithm.maxGrayFilter3D(input, rx, ry, rz);
        long endMaxFilter = System.nanoTime();
        int ndiffs = TestUtils.countDiffs(reference, actual);

        LOG.info("ImageArray {}x{}x{} kernel=({}, {}, {}): naive approach {} ms, maxfilter implementation {} ms, diffs {}",
                input.getWidth(), input.getHeight(), input.getDepth(),
                rx, ry, rz,
                elapsedMillis(startReference, endReference),
                elapsedMillis(endReference, endMaxFilter),
                ndiffs);
        assertEquals(0, ndiffs);
    }

    private double elapsedMillis(long startTime, long endTime) {
        return (endTime - startTime) / 1_000_000.;
    }

    private ImageArray naiveGray16MaxFilter3D(ImageArray input, int rx, int ry, int rz) {
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        Gray16ImageArray output = new Gray16ImageArray(width, height, depth);
        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int max = 0;
                    int zMin = Math.max(0, z - rz);
                    int zMax = Math.min(depth - 1, z + rz);
                    for (int az = zMin; az <= zMax; az++) {
                        int dz = az - z;
                        int yMin = Math.max(0, y - ry);
                        int yMax = Math.min(height - 1, y + ry);
                        for (int ay = yMin; ay <= yMax; ay++) {
                            int dy = ay - y;
                            int xr = computeGrayXRadius(rx, ry, rz, dy, dz);
                            if (xr < 0) {
                                continue;
                            }
                            int xMin = Math.max(0, x - xr);
                            int xMax = Math.min(width - 1, x + xr);
                            int rowBase = az * width * height + ay * width;
                            for (int ax = xMin; ax <= xMax; ax++) {
                                int val = input.getPackedIntValAtIndex(rowBase + ax) & 0xFFFF;
                                if (val > max) {
                                    max = val;
                                }
                            }
                        }
                    }
                    output.setPackedIntValAtCoords(x, y, z, max);
                }
            }
        }
        return output;
    }

    private int computeGrayXRadius(int rx, int ry, int rz, int dy, int dz) {
        double dyTerm = ry > 0 ? (double)(dy * dy) / (ry * ry) : 0;
        double dzTerm = rz > 0 ? (double)(dz * dz) / (rz * rz) : 0;
        double remaining = 1.0 - dyTerm - dzTerm;
        if (remaining < 0) {
            return -1;
        }

        int maxDx = (int)(rx * Math.sqrt(remaining));
        if (maxDx < rx) {
            double check = (double)((maxDx + 1) * (maxDx + 1)) / (rx * rx);
            if (check + dyTerm + dzTerm <= 1.0) {
                maxDx++;
            }
        }
        return maxDx;
    }
}
