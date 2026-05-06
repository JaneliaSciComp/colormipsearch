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

    private final int rx;
    private final int ry;
    private final int rz;
    // Precomputed x-radius for each (dy, dz) in the kernel.
    // Indexed as [dy + ry][dz + rz]. Value < 0 means outside ellipsoid.
    private final int[][] xRadii;
    private final int kySize; // 2*ry + 1
    private final int kzSize; // 2*rz + 1

    private final ValuesHistogram rHistogram;
    private final ValuesHistogram gHistogram;
    private final ValuesHistogram bHistogram;

    private int prevX;
    private int prevY;
    private int prevZ;
    private boolean uninitialized;

    public HistogramRGBMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        this.rx = xRadius;
        this.ry = yRadius;
        this.rz = zRadius;
        this.kySize = 2 * ry + 1;
        this.kzSize = Math.max(1, 2 * rz + 1);
        this.xRadii = precomputeXRadii();
        this.rHistogram = new ValuesHistogram(8);
        this.gHistogram = new ValuesHistogram(8);
        this.bHistogram = new ValuesHistogram(8);
        this.uninitialized = true;
    }

    private int[][] precomputeXRadii() {
        // Use IJ1-compatible discrete rasterization: r² + 1 per axis,
        // which slightly enlarges the kernel to include boundary pixels.
        double r2x = (double) rx * rx + 1;
        double r2y = ry > 0 ? (double) ry * ry + 1 : 0;
        double r2z = rz > 0 ? (double) rz * rz + 1 : 0;
        int actualKzSize = rz == 0 ? 1 : 2 * rz + 1;
        int[][] radii = new int[kySize][actualKzSize];
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
                if (s < 0) {
                    radii[idy][idz] = -1; // outside ellipsoid
                } else {
                    radii[idy][idz] = (int) Math.sqrt(s);
                }
            }
        }
        return radii;
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
        if (uninitialized || cy != prevY || cz != prevZ || Math.abs(cx - prevX) != 1) {
            // Full initialization: new row, new slice, or non-sequential x
            fullInitialize(imageArray, cx, cy, cz);
        } else if (cx == prevX + 1) {
            // Incremental forward: x moved by +1
            incrementalForwardX(imageArray, cx, cy, cz);
        } else {
            // Incremental backward: x moved by -1
            incrementalBackwardX(imageArray, cx, cy, cz);
        }
        prevX = cx;
        prevY = cy;
        prevZ = cz;
        uninitialized = false;
    }

    private void fullInitialize(ImageArray imageArray, int cx, int cy, int cz) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();

        rHistogram.clear();
        gHistogram.clear();
        bHistogram.clear();

        int actualKzSize = rz == 0 ? 1 : kzSize;
        for (int idy = 0; idy < kySize; idy++) {
            int ay = cy + (idy - ry);
            if (ay < 0 || ay >= height) continue;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int az = rz == 0 ? cz : cz + (idz - rz);
                if (az < 0 || az >= depth) continue;
                int xr = xRadii[idy][idz];
                if (xr < 0) continue; // outside ellipsoid
                int xMin = Math.max(0, cx - xr);
                int xMax = Math.min(width - 1, cx + xr);
                for (int ax = xMin; ax <= xMax; ax++) {
                    addPixel(imageArray, ax, ay, az);
                }
            }
        }
    }

    private void incrementalForwardX(ImageArray imageArray, int cx, int cy, int cz) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();

        int actualKzSize = rz == 0 ? 1 : kzSize;
        for (int idy = 0; idy < kySize; idy++) {
            int ay = cy + (idy - ry);
            if (ay < 0 || ay >= height) continue;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int az = rz == 0 ? cz : cz + (idz - rz);
                if (az < 0 || az >= depth) continue;
                int xr = xRadii[idy][idz];
                if (xr < 0) continue;
                // Add new right-edge pixel entering the kernel
                int newX = cx + xr;
                if (newX >= 0 && newX < width) {
                    addPixel(imageArray, newX, ay, az);
                }
                // Remove old left-edge pixel leaving the kernel
                int oldX = cx - xr - 1;
                if (oldX >= 0 && oldX < width) {
                    removePixel(imageArray, oldX, ay, az);
                }
            }
        }
    }

    private void incrementalBackwardX(ImageArray imageArray, int cx, int cy, int cz) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();

        int actualKzSize = rz == 0 ? 1 : kzSize;
        for (int idy = 0; idy < kySize; idy++) {
            int ay = cy + (idy - ry);
            if (ay < 0 || ay >= height) continue;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int az = rz == 0 ? cz : cz + (idz - rz);
                if (az < 0 || az >= depth) continue;
                int xr = xRadii[idy][idz];
                if (xr < 0) continue;
                // Add new left-edge pixel entering the kernel
                int newX = cx - xr;
                if (newX >= 0 && newX < width) {
                    addPixel(imageArray, newX, ay, az);
                }
                // Remove old right-edge pixel leaving the kernel
                int oldX = cx + xr + 1;
                if (oldX >= 0 && oldX < width) {
                    removePixel(imageArray, oldX, ay, az);
                }
            }
        }
    }

    private void addPixel(ImageArray imageArray, int x, int y, int z) {
        rHistogram.add(imageArray.getChannelIntValAtCoords(x, y, z, 0));
        gHistogram.add(imageArray.getChannelIntValAtCoords(x, y, z, 1));
        bHistogram.add(imageArray.getChannelIntValAtCoords(x, y, z, 2));
    }

    private void removePixel(ImageArray imageArray, int x, int y, int z) {
        rHistogram.remove(imageArray.getChannelIntValAtCoords(x, y, z, 0));
        gHistogram.remove(imageArray.getChannelIntValAtCoords(x, y, z, 1));
        bHistogram.remove(imageArray.getChannelIntValAtCoords(x, y, z, 2));
    }
}
