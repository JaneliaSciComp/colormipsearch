package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

/**
 * Sliding-window max filter for 8-bit grayscale images using a histogram.
 * <p>
 * Uses the column add/remove strategy: precomputes the x-extent of the
 * ellipsoidal kernel for each (dy, dz) row. When moving x+1, only adds
 * the new right-edge pixel and removes the old left-edge pixel for each
 * kernel row. Cost per x-step is O(ry * rz) instead of O(rx * ry * rz).
 * <p>
 * Full reinitialization happens at row/slice boundaries.
 */
public class HistogramGrayMaxFilterImageViewAdapter extends AbstractImageViewAdapter {

    private final KernelRow[] kernelRows;
    private final int[] activeRowBases;
    private final int[] activeXRadii;
    private final ValuesHistogram histogram;

    private int activeRowCount;
    private int prevX;
    private int prevY;
    private int prevZ;

    public HistogramGrayMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius, int bitdepth) {
        this.kernelRows = precomputeKernelRows(xRadius, yRadius, zRadius);
        this.activeRowBases = new int[kernelRows.length];
        this.activeXRadii = new int[kernelRows.length];
        this.histogram = new ValuesHistogram(bitdepth);
        this.prevX = -1;
        this.prevY = -1;
        this.prevZ = -1;
    }

    private KernelRow[] precomputeKernelRows(int rx, int ry, int rz) {
        // Use exact ellipsoid equation: (dx/rx)² + (dy/ry)² + (dz/rz)² <= 1
        // For each (dy, dz), find the largest integer dx where the equation holds.
        int kySize = 2 * ry + 1;
        int actualKzSize = rz == 0 ? 1 : 2 * rz + 1;
        KernelRow[] rows = new KernelRow[kySize * actualKzSize];
        int nRows = 0;
        for (int idy = 0; idy < kySize; idy++) {
            int dy = idy - ry;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int dz = rz == 0 ? 0 : idz - rz;
                // Find largest dx in [0..rx] where (dx/rx)² + (dy/ry)² + (dz/rz)² <= 1
                double dyTerm = ry > 0 ? (double)(dy * dy) / (ry * ry) : 0;
                double dzTerm = rz > 0 ? (double)(dz * dz) / (rz * rz) : 0;
                double remaining = 1.0 - dyTerm - dzTerm;
                if (remaining < 0) {
                    continue; // outside ellipsoid
                } else {
                    // max dx where (dx/rx)² <= remaining, i.e., dx <= rx * sqrt(remaining)
                    int maxDx = (int)(rx * Math.sqrt(remaining));
                    // Verify the boundary — due to floating-point, check if maxDx+1 also fits
                    if (maxDx < rx) {
                        double check = (double)((maxDx + 1) * (maxDx + 1)) / (rx * rx);
                        if (check + dyTerm + dzTerm <= 1.0) {
                            maxDx++;
                        }
                    }
                    rows[nRows++] = new KernelRow(dy, dz, maxDx);
                }
            }
        }
        KernelRow[] activeRows = new KernelRow[nRows];
        System.arraycopy(rows, 0, activeRows, 0, nRows);
        return activeRows;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getPackedIntValAtCoords(imageArray, x, y, z);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getChannelIntValAtCoords(imageArray, x, y, z, ch);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        updatePos(imageArray, x, y, z);
        return histogram.maxVal();
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        updatePos(imageArray, x, y, z);
        return histogram.maxVal();
    }

    private void updatePos(ImageArray imageArray, int cx, int cy, int cz) {
        if (cy != prevY || cz != prevZ || Math.abs(cx - prevX) != 1) {
            fullInitialize(imageArray, cx, cy, cz);
        } else if (cx == prevX + 1) {
            incrementalForwardX(imageArray, cx);
        } else {
            incrementalBackwardX(imageArray, cx);
        }
        prevX = cx;
        prevY = cy;
        prevZ = cz;
    }

    private void fullInitialize(ImageArray imageArray, int cx, int cy, int cz) {
        int width = imageArray.getWidth();

        histogram.clear();
        collectActiveRows(imageArray, cy, cz);

        for (int ri = 0; ri < activeRowCount; ri++) {
            int rowBase = activeRowBases[ri];
            int xr = activeXRadii[ri];
            int xMin = Math.max(0, cx - xr);
            int xMax = Math.min(width - 1, cx + xr);
            int end = rowBase + xMax;
            for (int pi = rowBase + xMin; pi <= end; pi++) {
                histogram.add(imageArray.getPackedIntValAtIndex(pi));
            }
        }
    }

    private void incrementalForwardX(ImageArray imageArray, int cx) {
        int width = imageArray.getWidth();

        for (int ri = 0; ri < activeRowCount; ri++) {
            int rowBase = activeRowBases[ri];
            int xr = activeXRadii[ri];
            // Add new right-edge pixel entering the kernel
            int newX = cx + xr;
            if (newX < width) {
                histogram.add(imageArray.getPackedIntValAtIndex(rowBase + newX));
            }
            // Remove old left-edge pixel leaving the kernel
            int oldX = cx - xr - 1;
            if (oldX >= 0) {
                histogram.remove(imageArray.getPackedIntValAtIndex(rowBase + oldX));
            }
        }
    }

    private void incrementalBackwardX(ImageArray imageArray, int cx) {
        int width = imageArray.getWidth();

        for (int ri = 0; ri < activeRowCount; ri++) {
            int rowBase = activeRowBases[ri];
            int xr = activeXRadii[ri];
            // Add new left-edge pixel entering the kernel
            int newX = cx - xr;
            if (newX >= 0) {
                histogram.add(imageArray.getPackedIntValAtIndex(rowBase + newX));
            }
            // Remove old right-edge pixel leaving the kernel
            int oldX = cx + xr + 1;
            if (oldX < width) {
                histogram.remove(imageArray.getPackedIntValAtIndex(rowBase + oldX));
            }
        }
    }

    private void collectActiveRows(ImageArray imageArray, int cy, int cz) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        int sliceSize = width * height;

        activeRowCount = 0;
        for (KernelRow kernelRow : kernelRows) {
            int ay = cy + kernelRow.dy;
            if (ay < 0 || ay >= height) {
                continue;
            }
            int az = cz + kernelRow.dz;
            if (az < 0 || az >= depth) {
                continue;
            }
            activeRowBases[activeRowCount] = az * sliceSize + ay * width;
            activeXRadii[activeRowCount] = kernelRow.xr;
            activeRowCount++;
        }
    }

    private static class KernelRow {
        private final int dy;
        private final int dz;
        private final int xr;

        private KernelRow(int dy, int dz, int xr) {
            this.dy = dy;
            this.dz = dz;
            this.xr = xr;
        }
    }
}
