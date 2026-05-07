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

    private final int rx;
    private final int ry;
    private final int rz;
    // Precomputed x-radius for each (dy, dz) in the kernel.
    // Indexed as [dy + ry][dz + rz]. Value < 0 means outside ellipsoid.
    private final int[][] xRadii;
    private final int kySize; // 2*ry + 1
    private final int kzSize; // 2*rz + 1

    private final ValuesHistogram histogram;

    private int prevX;
    private int prevY;
    private int prevZ;
    private boolean uninitialized;

    public HistogramGrayMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius, int bitdepth) {
        this.rx = xRadius;
        this.ry = yRadius;
        this.rz = zRadius;
        this.kySize = 2 * ry + 1;
        this.kzSize = Math.max(1, 2 * rz + 1);
        this.xRadii = precomputeXRadii();
        this.histogram = new ValuesHistogram(bitdepth);
        this.uninitialized = true;
    }

    private int[][] precomputeXRadii() {
        // Use exact ellipsoid equation: (dx/rx)² + (dy/ry)² + (dz/rz)² <= 1
        // For each (dy, dz), find the largest integer dx where the equation holds.
        int actualKzSize = rz == 0 ? 1 : 2 * rz + 1;
        int[][] radii = new int[kySize][actualKzSize];
        for (int idy = 0; idy < kySize; idy++) {
            int dy = idy - ry;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int dz = rz == 0 ? 0 : idz - rz;
                // Find largest dx in [0..rx] where (dx/rx)² + (dy/ry)² + (dz/rz)² <= 1
                double dyTerm = ry > 0 ? (double)(dy * dy) / (ry * ry) : 0;
                double dzTerm = rz > 0 ? (double)(dz * dz) / (rz * rz) : 0;
                double remaining = 1.0 - dyTerm - dzTerm;
                if (remaining < 0) {
                    radii[idy][idz] = -1; // outside ellipsoid
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
                    radii[idy][idz] = maxDx;
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
        return histogram.maxVal();
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        updatePos(imageArray, x, y, z);
        return histogram.maxVal();
    }

    private void updatePos(ImageArray imageArray, int cx, int cy, int cz) {
        if (uninitialized || cy != prevY || cz != prevZ || Math.abs(cx - prevX) != 1) {
            fullInitialize(imageArray, cx, cy, cz);
        } else if (cx == prevX + 1) {
            incrementalForwardX(imageArray, cx, cy, cz);
        } else {
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

        histogram.clear();

        int actualKzSize = rz == 0 ? 1 : kzSize;
        for (int idy = 0; idy < kySize; idy++) {
            int ay = cy + (idy - ry);
            if (ay < 0 || ay >= height) continue;
            for (int idz = 0; idz < actualKzSize; idz++) {
                int az = rz == 0 ? cz : cz + (idz - rz);
                if (az < 0 || az >= depth) continue;
                int xr = xRadii[idy][idz];
                if (xr < 0) continue;
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
        histogram.add(imageArray.getPackedIntValAtCoords(x, y, z));
    }

    private void removePixel(ImageArray imageArray, int x, int y, int z) {
        histogram.remove(imageArray.getPackedIntValAtCoords(x, y, z));
    }
}
