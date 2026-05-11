package org.janelia.colormipsearch.image.algorithms;

import java.util.Arrays;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 3D connected components analysis using BFS flood-fill.
 * Finds the largest connected component in a thresholded volume.
 */
public class Connect3DComponentsAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(Connect3DComponentsAlgorithm.class);
    private static final int INITIAL_QUEUE_CAPACITY = 4096;

    public static class ComponentsResult {
        private final int[] labels;
        private final int largestLabel;
        private final int largestComponentSize;
        private final int componentCount;

        private ComponentsResult(int[] labels, int componentlabel, int largestComponentSize, int componentCount) {
            this.labels = labels;
            this.largestLabel = componentlabel;
            this.largestComponentSize = largestComponentSize;
            this.componentCount = componentCount;
        }

        public int[] getLabels() {
            return labels;
        }

        public int getLargestLabel() {
            return largestLabel;
        }

        public int getLargestComponentSize() {
            return largestComponentSize;
        }

        public int getComponentCount() {
            return componentCount;
        }
    }

    public static class ComponentLabelRegionPredicate implements ImageMaskPredicate {
        private final int[] labels;
        private final int selectedLabel;

        public ComponentLabelRegionPredicate(int[] labels, int selectedLabel) {
            this.labels = labels;
            this.selectedLabel = selectedLabel;
        }

        @Override
        public boolean checkPixelPos(ImageArray imageArray, int x, int y, int z) {
            int pi = imageArray.getSpatialLinearIndex(x, y, z);
            return selectedLabel != labels[pi];
        }

        @Override
        public boolean checkPixelVal(int val) {
            return false;
        }
    }

    private static class Int3Queue {
        private int[] xs;
        private int[] ys;
        private int[] zs;
        private int head;
        private int tail;
        private int x;
        private int y;
        private int z;

        private Int3Queue(int initialCapacity) {
            int capacity = Math.max(1, initialCapacity);
            this.xs = new int[capacity];
            this.ys = new int[capacity];
            this.zs = new int[capacity];
        }

        private void clear() {
            head = 0;
            tail = 0;
        }

        private void add(int x, int y, int z) {
            ensureCapacityForOneMore();
            xs[tail] = x;
            ys[tail] = y;
            zs[tail] = z;
            tail++;
        }

        private void poll() {
            x = xs[head];
            y = ys[head];
            z = zs[head];
            head++;
        }

        private int getX() {
            return x;
        }

        private int getY() {
            return y;
        }

        private int getZ() {
            return z;
        }

        private boolean isEmpty() {
            return head == tail;
        }

        private void ensureCapacityForOneMore() {
            if (tail < xs.length) {
                return;
            }
            if (head > 0) {
                int size = tail - head;
                System.arraycopy(xs, head, xs, 0, size);
                System.arraycopy(ys, head, ys, 0, size);
                System.arraycopy(zs, head, zs, 0, size);
                head = 0;
                tail = size;
            } else {
                int newCapacity = xs.length * 2;
                xs = Arrays.copyOf(xs, newCapacity);
                ys = Arrays.copyOf(ys, newCapacity);
                zs = Arrays.copyOf(zs, newCapacity);
            }
        }
    }

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
     * @return a new image containing the segmentation labels for all connected components
     */
    public static ComponentsResult findConnectedComponents(ImageArray input, int threshold) {
        return traverseAllComponents(input, threshold);
    }

    /**
     *
     * @param input image array
     * @param threshold voxel threshold value
     * @return
     */
    private static ComponentsResult traverseAllComponents(ImageArray input, int threshold) {
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        int sliceSize = width * height;

        int componentCount = 0;
        int largestLabel = -1;
        int largestSize = 0;
        int nextLabel = 1;
        // label array: 0 = unlabeled, -1 = below threshold
        int[] labels = new int[input.getSpatialSize()];
        Int3Queue queue = new Int3Queue(Math.min(input.getSpatialSize(), INITIAL_QUEUE_CAPACITY));

        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = z * sliceSize + y * width + x;
                    if (labels[idx] != 0) continue;

                    int val = input.getPackedIntValAtIndex(idx);
                    if (val < threshold) {
                        labels[idx] = -1;
                        continue;
                    }

                    // BFS flood fill
                    int label = nextLabel++;
                    componentCount++;
                    queue.clear();
                    queue.add(x, y, z);
                    labels[idx] = label;
                    int componentSize = 1;

                    while (!queue.isEmpty()) {
                        queue.poll();

                        int px = queue.getX();
                        int py = queue.getY();
                        int pz = queue.getZ();

                        for (int[] offset : NEIGHBORS_26) {
                            int nx = px + offset[0];
                            int ny = py + offset[1];
                            int nz = pz + offset[2];
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height || nz < 0 || nz >= depth) continue;
                            int nIdx = nz * sliceSize + ny * width + nx;
                            if (labels[nIdx] != 0) continue;
                            int nVal = input.getPackedIntValAtIndex(nIdx);
                            if (nVal < threshold) {
                                labels[nIdx] = -1;
                                continue;
                            }
                            labels[nIdx] = label;
                            componentSize++;
                            queue.add(nx, ny, nz);
                        }
                    }

                    if (componentSize > largestSize) {
                        largestSize = componentSize;
                        largestLabel = label;
                    }
                }
            }
        }

        LOG.debug("Found {} components{}",
                componentCount,
                largestLabel > 0 ? String.format(", largest has %d voxels (label=%d)", largestLabel, largestSize) : "");

        return new ComponentsResult(labels, largestLabel, largestSize, componentCount);
    }

}
