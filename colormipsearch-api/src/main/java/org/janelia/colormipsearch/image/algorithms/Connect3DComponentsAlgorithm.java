package org.janelia.colormipsearch.image.algorithms;

import java.util.Arrays;

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
    private static final int INITIAL_QUEUE_CAPACITY = 4096;

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
        return findLargestComponentWithSize(input, threshold, minVolume).getComponentImage();
    }

    /**
     * Find the largest connected component and return the component image together with its size.
     *
     * @param input     3D volume (single channel)
     * @param threshold voxels with value >= threshold are considered foreground
     * @param minVolume minimum volume (in voxels) for a component to be considered
     * @return the largest-component image and metadata
     */
    public static LargestComponentResult findLargestComponentWithSize(ImageArray input, int threshold, int minVolume) {
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        int sliceSize = width * height;
        int totalSize = input.getSpatialSize();

        // Label array: 0 = unlabeled, -1 = below threshold
        int[] labels = new int[totalSize];
        int nextLabel = 1;

        int componentCount = 0;
        int largestLabel = -1;
        int largestSize = 0;
        Int3Queue queue = new Int3Queue(Math.min(totalSize, INITIAL_QUEUE_CAPACITY));

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
                    int componentSize = 0;
                    queue.clear();
                    queue.add(x, y, z);
                    labels[idx] = label;

                    while (!queue.isEmpty()) {
                        queue.poll();
                        componentSize++;

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
                            queue.add(nx, ny, nz);
                        }
                    }

                    if (componentSize >= minVolume && componentSize > largestSize) {
                        largestSize = componentSize;
                        largestLabel = label;
                    }
                }
            }
        }

        LOG.debug("Found {} components, largest has {} voxels (label={})", componentCount, largestSize, largestLabel);

        // Create output with only the largest component
        Gray16ImageArray output = new Gray16ImageArray(width, height, depth);
        if (largestLabel > 0) {
            for (int pi = 0; pi < totalSize; pi++) {
                if (labels[pi] == largestLabel) {
                    output.setPackedIntValAtIndex(pi, input.getPackedIntValAtIndex(pi));
                }
            }
        }
        return new LargestComponentResult(output, largestSize, componentCount);
    }

    public static class LargestComponentResult {
        private final ImageArray componentImage;
        private final int componentSize;
        private final int componentCount;

        private LargestComponentResult(ImageArray componentImage, int componentSize, int componentCount) {
            this.componentImage = componentImage;
            this.componentSize = componentSize;
            this.componentCount = componentCount;
        }

        public ImageArray getComponentImage() {
            return componentImage;
        }

        public int getComponentSize() {
            return componentSize;
        }

        public int getComponentCount() {
            return componentCount;
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
}
