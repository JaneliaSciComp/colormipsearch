package org.janelia.colormipsearch.image.algorithms;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 3D connected components analysis using BFS flood-fill.
 * Finds the largest connected component in a thresholded volume.
 */
public class Connect3DComponentsAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(Connect3DComponentsAlgorithm.class);

    // 6-connectivity offsets
    // 26-connectivity: face, edge, and corner neighbors (full 3D Moore neighborhood)
    private static final int[][] NEIGHBORS_26;
    static {
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    if (dx != 0 || dy != 0 || dz != 0)
                        list.add(new int[]{dx, dy, dz});
        NEIGHBORS_26 = list.toArray(new int[0][]);
    }

    /**
     * Find the largest connected component in the input volume.
     *
     * @param input     3D volume (single channel)
     * @param threshold voxels with value >= threshold are considered foreground
     * @param minVolume minimum volume (in voxels) for a component to be considered
     * @return a new image containing only the largest component (255 for foreground, 0 for background)
     */
    public static ImageArray findLargestComponent(ImageArray input, int threshold, int minVolume) {
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        int totalSize = width * height * depth;

        // Label array: 0 = unlabeled, -1 = below threshold
        int[] labels = new int[totalSize];
        int nextLabel = 1;

        // Track component sizes
        Map<Integer, Integer> componentSizes = new HashMap<>();

        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = z * height * width + y * width + x;
                    int val = input.getPackedIntValAtCoords(x, y, z);
                    if (val < threshold) {
                        labels[idx] = -1;
                        continue;
                    }
                    if (labels[idx] != 0) continue;

                    // BFS flood fill
                    int label = nextLabel++;
                    int componentSize = 0;
                    Queue<int[]> queue = new ArrayDeque<>();
                    queue.add(new int[]{x, y, z});
                    labels[idx] = label;

                    while (!queue.isEmpty()) {
                        int[] pos = queue.poll();
                        componentSize++;

                        for (int[] offset : NEIGHBORS_26) {
                            int nx = pos[0] + offset[0];
                            int ny = pos[1] + offset[1];
                            int nz = pos[2] + offset[2];
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height || nz < 0 || nz >= depth) continue;
                            int nIdx = nz * height * width + ny * width + nx;
                            if (labels[nIdx] != 0) continue;
                            int nVal = input.getPackedIntValAtCoords(nx, ny, nz);
                            if (nVal < threshold) {
                                labels[nIdx] = -1;
                                continue;
                            }
                            labels[nIdx] = label;
                            queue.add(new int[]{nx, ny, nz});
                        }
                    }
                    componentSizes.put(label, componentSize);
                }
            }
        }

        // Find largest component above minVolume
        int largestLabel = -1;
        int largestSize = 0;
        for (Map.Entry<Integer, Integer> entry : componentSizes.entrySet()) {
            if (entry.getValue() >= minVolume && entry.getValue() > largestSize) {
                largestSize = entry.getValue();
                largestLabel = entry.getKey();
            }
        }

        LOG.debug("Found {} components, largest has {} voxels (label={})", componentSizes.size(), largestSize, largestLabel);

        // Create output with only the largest component
        Gray16ImageArray output = new Gray16ImageArray(width, height, depth);
        if (largestLabel > 0) {
            for (int pi = 0; pi < totalSize; pi++) {
                if (labels[pi] == largestLabel) {
                    output.setPackedIntValAtIndex(pi, input.getPackedIntValAtIndex(pi));
                }
            }
        }
        return output;
    }
}
