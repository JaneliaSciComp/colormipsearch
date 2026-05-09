package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

/**
 * Sliding-window max filter for RGB images using per-channel histograms.
 * <p>
 * Uses the column add/remove strategy: precomputes the x-extent of the
 * ellipsoidal kernel for each (dy, dz) row. When moving x+1, only adds
 * the new right-edge pixel and removes the old left-edge pixel for each
 * kernel row. Cost per x-step is O(ry * rz) instead of O(rx * ry * rz).
 * <p>
 * Full reinitialization happens at row/slice boundaries.
 */
public class HistogramRGBMaxFilterImageViewAdapter extends AbstractImageViewAdapter {

    private final KernelRow[] kernelRows;
    private final int[] activeRowBases;
    private final int[] activeXRadii;
    private final ValuesHistogram rHistogram;
    private final ValuesHistogram gHistogram;
    private final ValuesHistogram bHistogram;

    private int activeRowCount;
    private int prevX;
    private int prevY;
    private int prevZ;

    public HistogramRGBMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        this.kernelRows = precomputeKernelRows(xRadius, yRadius, zRadius);
        this.activeRowBases = new int[kernelRows.length];
        this.activeXRadii = new int[kernelRows.length];
        this.rHistogram = new ValuesHistogram(8);
        this.gHistogram = new ValuesHistogram(8);
        this.bHistogram = new ValuesHistogram(8);
        this.prevX = -1;
        this.prevY = -1;
        this.prevZ = -1;
    }

    private KernelRow[] precomputeKernelRows(int rx, int ry, int rz) {
        // Use IJ1-compatible discrete rasterization: r² + 1 per axis,
        // which slightly enlarges the kernel to include boundary pixels.
        double r2x = (double) rx * rx + 1;
        double r2y = ry > 0 ? (double) ry * ry + 1 : 0;
        double r2z = rz > 0 ? (double) rz * rz + 1 : 0;
        int kySize = 2 * ry + 1;
        int actualKzSize = rz == 0 ? 1 : 2 * rz + 1;
        KernelRow[] rows = new KernelRow[kySize * actualKzSize];
        int nRows = 0;
        for (int idy = 0; idy < kySize; idy++) {
            int dy = idy - ry;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int dz = rz == 0 ? 0 : idz - rz;
                double s = r2x;
                if (ry > 0) {
                    s -= (double) (dy * dy) * r2x / r2y;
                }
                if (rz > 0) {
                    s -= (double) (dz * dz) * r2x / r2z;
                }
                if (s >= 0) {
                    // inside ellipsoid
                    rows[nRows++] = new KernelRow(dy, dz, (int) Math.sqrt(s));
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
        int maxR = rHistogram.maxVal();
        int maxG = gHistogram.maxVal();
        int maxB = bHistogram.maxVal();
        return maxR << 16 | maxG << 8 | maxB;
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        updatePos(imageArray, x, y, z);
        switch (ch) {
            case 0: return rHistogram.maxVal();
            case 1: return gHistogram.maxVal();
            case 2: return bHistogram.maxVal();
            default: throw new IllegalArgumentException("Invalid channel: " + ch);
        }
    }

    private void updatePos(ImageArray imageArray, int cx, int cy, int cz) {
        if (cy != prevY || cz != prevZ || Math.abs(cx - prevX) != 1) {
            // Full initialization: new row, new slice, or non-sequential x
            fullInitialize(imageArray, cx, cy, cz);
        } else if (cx == prevX + 1) {
            // Incremental forward: x moved by +1
            incrementalForwardX(imageArray, cx);
        } else {
            // Incremental backward: x moved by -1
            incrementalBackwardX(imageArray, cx);
        }
        prevX = cx;
        prevY = cy;
        prevZ = cz;
    }

    private void fullInitialize(ImageArray imageArray, int cx, int cy, int cz) {
        int width = imageArray.getWidth();

        rHistogram.clear();
        gHistogram.clear();
        bHistogram.clear();
        collectActiveRows(imageArray, cy, cz);

        for (int ri = 0; ri < activeRowCount; ri++) {
            int rowBase = activeRowBases[ri];
            int xr = activeXRadii[ri];
            int xMin = Math.max(0, cx - xr);
            int xMax = Math.min(width - 1, cx + xr);
            int end = rowBase + xMax;
            for (int pi = rowBase + xMin; pi <= end; pi++) {
                addPixel(imageArray, pi);
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
                addPixel(imageArray, rowBase + newX);
            }
            // Remove old left-edge pixel leaving the kernel
            int oldX = cx - xr - 1;
            if (oldX >= 0) {
                removePixel(imageArray, rowBase + oldX);
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
                addPixel(imageArray, rowBase + newX);
            }
            // Remove old right-edge pixel leaving the kernel
            int oldX = cx + xr + 1;
            if (oldX < width) {
                removePixel(imageArray, rowBase + oldX);
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

    private void addPixel(ImageArray imageArray, int pi) {
        rHistogram.add(imageArray.getChannelIntValAtIndex(pi, 0));
        gHistogram.add(imageArray.getChannelIntValAtIndex(pi, 1));
        bHistogram.add(imageArray.getChannelIntValAtIndex(pi, 2));
    }

    private void removePixel(ImageArray imageArray, int pi) {
        rHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 0));
        gHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 1));
        bHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 2));
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
