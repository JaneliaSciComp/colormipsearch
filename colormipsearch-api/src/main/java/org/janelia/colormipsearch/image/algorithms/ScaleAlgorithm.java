package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.WriteableImageArray;

public class ScaleAlgorithm {

    private static final double ALPHA = 0.5;

    private static class ScaleParams {
        private final double scale;

        private ScaleParams(int sourceSize, int targetSize) {
            this.scale = (double) sourceSize / targetSize;
        }

        private double sourceCoord(int targetCoord) {
            return (targetCoord + 0.5) * scale - 0.5;
        }
    }

    /**
     * Rescale a volume to target dimensions using separable interpolation and
     * half-pixel center coordinate mapping.
     */
    public static ImageArray scaleVolume(ImageArray input, int targetW, int targetH, int targetD, int targetMaxValue,
                                         ImageArrayFactory factory) {
        int srcW = input.getWidth();
        int srcH = input.getHeight();
        int srcD = input.getDepth();
        if (srcW == targetW && srcH == targetH && srcD == targetD) {
            return input;
        }

        ScaleParams xScale = new ScaleParams(srcW, targetW);
        ScaleParams yScale = new ScaleParams(srcH, targetH);
        ScaleParams zScale = new ScaleParams(srcD, targetD);

        float[] zScaled = new float[srcW * srcH * targetD];
        for (int z = 0; z < targetD; z++) {
            double sz = zScale.sourceCoord(z);
            for (int y = 0; y < srcH; y++) {
                for (int x = 0; x < srcW; x++) {
                    zScaled[index(x, y, z, srcW, srcH)] = (float) getCubicInterpolatedPixelZ(
                            x, y, sz, srcW, srcH, srcD, input
                    );
                }
            }
        }

        float[] zyScaled = new float[srcW * targetH * targetD];
        for (int z = 0; z < targetD; z++) {
            for (int y = 0; y < targetH; y++) {
                double sy = yScale.sourceCoord(y);
                for (int x = 0; x < srcW; x++) {
                    zyScaled[index(x, y, z, srcW, targetH)] = (float) getCubicInterpolatedPixelY(
                            x, sy, z, srcW, srcH, targetD, zScaled
                    );
                }
            }
        }

        WriteableImageArray output = factory.create(targetW, targetH, targetD);
        for (int z = 0; z < targetD; z++) {
            for (int y = 0; y < targetH; y++) {
                for (int x = 0; x < targetW; x++) {
                    double sx = xScale.sourceCoord(x);
                    double value = getCubicInterpolatedPixelX(
                            sx, y, z, srcW, targetH, targetD, zyScaled
                    );
                    output.setPackedIntValAtCoords(x, y, z, clampToType(value, targetMaxValue));
                }
            }
        }

        return output;
    }

    private static double getCubicInterpolatedPixelZ(int x, int y, double z, int width, int height, int depth, ImageArray input) {
        int z0 = (int) Math.floor(z);
        if (z0 <= 0 || z0 >= depth - 2) {
            return getLinearInterpolatedPixelZ(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int az = z0 - 1 + i;
            p += input.getPackedIntValAtCoords(x, y, az) * cubic(z - az);
        }
        return p;
    }

    private static double getLinearInterpolatedPixelZ(int x, int y, double z, int width, int height, int depth, ImageArray input) {
        if (depth <= 1) {
            return x >= 0 && x < width && y >= 0 && y < height && z >= -1 && z < depth
                    ? input.getPackedIntValAtCoords(x, y, 0)
                    : 0;
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (z < 0.0) z = 0.0;
            if (z >= depth - 1.0) z = depth - 1.001;

            int z0 = (int) z;
            double dz = z - z0;
            double c0 = input.getPackedIntValAtCoords(x, y, z0);
            double c1 = input.getPackedIntValAtCoords(x, y, z0 + 1);
            return c0 * (1 - dz) + c1 * dz;
        } else {
            return 0.0;
        }
    }

    private static double getCubicInterpolatedPixelY(int x, double y, int z, int width, int height, int depth, float[] input) {
        int y0 = (int) Math.floor(y);
        if (y0 <= 0 || y0 >= height - 2) {
            return getLinearInterpolatedPixelY(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int ay = y0 - 1 + i;
            p += input[index(x, ay, z, width, height)] * cubic(y - ay);
        }
        return p;
    }

    private static double getLinearInterpolatedPixelY(int x, double y, int z, int width, int height, int depth, float[] input) {
        if (height <= 1) {
            return x >= 0 && x < width && y >= -1 && y < height && z >= 0 && z < depth
                    ? input[index(x, 0, z, width, height)]
                    : 0;
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (y < 0.0) y = 0.0;
            if (y >= height - 1.0) y = height - 1.001;

            int y0 = (int) y;
            double dy = y - y0;
            double c0 = input[index(x, y0, z, width, height)];
            double c1 = input[index(x, y0 + 1, z, width, height)];
            return c0 * (1 - dy) + c1 * dy;
        } else {
            return 0.0;
        }
    }

    private static double getCubicInterpolatedPixelX(double x, int y, int z, int width, int height, int depth, float[] input) {
        int x0 = (int) Math.floor(x);
        if (x0 <= 0 || x0 >= width - 2) {
            return getLinearInterpolatedPixelX(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int ax = x0 - 1 + i;
            p += input[index(ax, y, z, width, height)] * cubic(x - ax);
        }
        return p;
    }

    private static double getLinearInterpolatedPixelX(double x, int y, int z, int width, int height, int depth, float[] input) {
        if (width <= 1) {
            return x >= -1 && x < width && y >= 0 && y < height && z >= 0 && z < depth
                    ? input[index(0, y, z, width, height)]
                    : 0;
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (x < 0.0) x = 0.0;
            if (x >= width - 1.0) x = width - 1.001;

            int x0 = (int) x;
            double dx = x - x0;
            double c0 = input[index(x0, y, z, width, height)];
            double c1 = input[index(x0 + 1, y, z, width, height)];
            return c0 * (1 - dx) + c1 * dx;
        } else {
            return 0.0;
        }
    }

    private static int clampToType(double value, int maxValue) {
        int intValue = (int) ((float) value + 0.5f);
        if (intValue < 0) {
            return 0;
        }
        if (intValue > maxValue) {
            return maxValue;
        }
        return intValue;
    }

    private static int index(int x, int y, int z, int width, int height) {
        return z * width * height + y * width + x;
    }

    private static double cubic(double x) {
        if (x < 0.0) x = -x;
        if (x < 1.0)
            return x * x * (x * (-ALPHA + 2.0) + (ALPHA - 3.0)) + 1.0;
        else if (x < 2.0)
            return -ALPHA * x * x * x + 5.0 * ALPHA * x * x - 8.0 * ALPHA * x + 4.0 * ALPHA;
        return 0.0;
    }

}
