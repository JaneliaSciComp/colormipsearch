package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ScaleAlgorithmTest {

    @Test
    public void scaleVolumeUsesHalfPixelCenterMappingWhenUpscaling() {
        Gray16ImageArray input = new Gray16ImageArray(4, 2, 2);
        for (int z = 0; z < input.getDepth(); z++) {
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    input.setPackedIntValAtCoords(x, y, z, x * 100);
                }
            }
        }

        ImageArray scaled = ScaleAlgorithm.scaleVolume(input, 8, 2, 2, 65535, Gray16ImageArray::new);

        assertEquals(0, scaled.getPackedIntValAtCoords(0, 0, 0));
        assertEquals(25, scaled.getPackedIntValAtCoords(1, 0, 0));
        assertEquals(75, scaled.getPackedIntValAtCoords(2, 0, 0));
        assertEquals(125, scaled.getPackedIntValAtCoords(3, 0, 0));
        assertEquals(175, scaled.getPackedIntValAtCoords(4, 0, 0));
        assertEquals(225, scaled.getPackedIntValAtCoords(5, 0, 0));
        assertEquals(275, scaled.getPackedIntValAtCoords(6, 0, 0));
        assertEquals(300, scaled.getPackedIntValAtCoords(7, 0, 0));
    }

    @Test
    public void scaleVolumeUsesHalfPixelCenterMappingWhenDownscaling() {
        Gray16ImageArray input = new Gray16ImageArray(8, 2, 2);
        for (int z = 0; z < input.getDepth(); z++) {
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    input.setPackedIntValAtCoords(x, y, z, x * 100);
                }
            }
        }

        ImageArray scaled = ScaleAlgorithm.scaleVolume(input, 4, 2, 2, 65535, Gray16ImageArray::new);

        assertEquals(50, scaled.getPackedIntValAtCoords(0, 0, 0));
        assertEquals(250, scaled.getPackedIntValAtCoords(1, 0, 0));
        assertEquals(450, scaled.getPackedIntValAtCoords(2, 0, 0));
        assertEquals(650, scaled.getPackedIntValAtCoords(3, 0, 0));
    }

    @Test
    public void scaleVolumeMatchesHalfPixelReferenceForSparseVolume() {
        Gray16ImageArray input = new Gray16ImageArray(5, 4, 3);
        input.setPackedIntValAtCoords(1, 1, 1, 255);
        input.setPackedIntValAtCoords(3, 2, 1, 900);
        input.setPackedIntValAtCoords(4, 3, 2, 1200);
        input.setPackedIntValAtCoords(0, 0, 0, 75);

        ImageArray expected = halfPixelReference(input, 7, 6, 5);
        ImageArray actual = ScaleAlgorithm.scaleVolume(input, 7, 6, 5, 65535, Gray16ImageArray::new);

        assertImageEquals(expected, actual);
    }

    @Test
    public void scaleVolumeReturnsInputWhenDimensionsAreUnchanged() {
        Gray16ImageArray input = new Gray16ImageArray(3, 4, 5);

        assertSame(input, ScaleAlgorithm.scaleVolume(input, 3, 4, 5, 65535, Gray16ImageArray::new));
    }

    private ImageArray halfPixelReference(ImageArray input, int targetW, int targetH, int targetD) {
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();

        ScaleParams xScale = new ScaleParams(width, targetW);
        ScaleParams yScale = new ScaleParams(height, targetH);
        ScaleParams zScale = new ScaleParams(depth, targetD);

        float[] zScaled = new float[width * height * targetD];
        for (int z = 0; z < targetD; z++) {
            double sz = zScale.sourceCoord(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    zScaled[index(x, y, z, width, height)] = (float) refCubicZ(x, y, sz, width, height, depth, input);
                }
            }
        }

        float[] zyScaled = new float[width * targetH * targetD];
        for (int z = 0; z < targetD; z++) {
            for (int y = 0; y < targetH; y++) {
                double sy = yScale.sourceCoord(y);
                for (int x = 0; x < width; x++) {
                    zyScaled[index(x, y, z, width, targetH)] = (float) refCubicY(x, sy, z, width, height, targetD, zScaled);
                }
            }
        }

        WriteableImageArray output = new Gray16ImageArray(targetW, targetH, targetD);
        for (int z = 0; z < targetD; z++) {
            for (int y = 0; y < targetH; y++) {
                for (int x = 0; x < targetW; x++) {
                    double sx = xScale.sourceCoord(x);
                    output.setPackedIntValAtCoords(x, y, z, clamp(refCubicX(sx, y, z, width, targetH, targetD, zyScaled)));
                }
            }
        }
        return output;
    }

    private void assertImageEquals(ImageArray expected, ImageArray actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertEquals(expected.getDepth(), actual.getDepth());
        for (int pi = 0; pi < expected.getSpatialSize(); pi++) {
            assertEquals("Pixel " + pi, expected.getPackedIntValAtIndex(pi), actual.getPackedIntValAtIndex(pi));
        }
    }

    private double refCubicZ(int x, int y, double z, int width, int height, int depth, ImageArray input) {
        int z0 = (int) Math.floor(z);
        if (z0 <= 0 || z0 >= depth - 2) {
            return refLinearZ(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int az = z0 - 1 + i;
            p += input.getPackedIntValAtCoords(x, y, az) * cubic(z - az);
        }
        return p;
    }

    private double refLinearZ(int x, int y, double z, int width, int height, int depth, ImageArray input) {
        if (depth <= 1) {
            return input.getPackedIntValAtCoords(x, y, 0);
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (z < 0.0) z = 0.0;
            if (z >= depth - 1.0) z = depth - 1.001;
            int z0 = (int) z;
            double dz = z - z0;
            return input.getPackedIntValAtCoords(x, y, z0) * (1 - dz) +
                    input.getPackedIntValAtCoords(x, y, z0 + 1) * dz;
        } else {
            return 0.0;
        }
    }

    private double refCubicY(int x, double y, int z, int width, int height, int depth, float[] input) {
        int y0 = (int) Math.floor(y);
        if (y0 <= 0 || y0 >= height - 2) {
            return refLinearY(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int ay = y0 - 1 + i;
            p += input[index(x, ay, z, width, height)] * cubic(y - ay);
        }
        return p;
    }

    private double refLinearY(int x, double y, int z, int width, int height, int depth, float[] input) {
        if (height <= 1) {
            return input[index(x, 0, z, width, height)];
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (y < 0.0) y = 0.0;
            if (y >= height - 1.0) y = height - 1.001;
            int y0 = (int) y;
            double dy = y - y0;
            return input[index(x, y0, z, width, height)] * (1 - dy) +
                    input[index(x, y0 + 1, z, width, height)] * dy;
        } else {
            return 0.0;
        }
    }

    private double refCubicX(double x, int y, int z, int width, int height, int depth, float[] input) {
        int x0 = (int) Math.floor(x);
        if (x0 <= 0 || x0 >= width - 2) {
            return refLinearX(x, y, z, width, height, depth, input);
        }

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int ax = x0 - 1 + i;
            p += input[index(ax, y, z, width, height)] * cubic(x - ax);
        }
        return p;
    }

    private double refLinearX(double x, int y, int z, int width, int height, int depth, float[] input) {
        if (width <= 1) {
            return input[index(0, y, z, width, height)];
        }
        if (x >= -1 && x < width && y >= -1 && y < height && z >= -1 && z < depth) {
            if (x < 0.0) x = 0.0;
            if (x >= width - 1.0) x = width - 1.001;
            int x0 = (int) x;
            double dx = x - x0;
            return input[index(x0, y, z, width, height)] * (1 - dx) +
                    input[index(x0 + 1, y, z, width, height)] * dx;
        } else {
            return 0.0;
        }
    }

    private int clamp(double value) {
        int intValue = (int) ((float) value + 0.5f);
        if (intValue < 0) return 0;
        if (intValue > 65535) return 65535;
        return intValue;
    }

    private double cubic(double x) {
        if (x < 0.0) x = -x;
        if (x < 1.0)
            return x * x * (x * (-0.5 + 2.0) + (0.5 - 3.0)) + 1.0;
        else if (x < 2.0)
            return -0.5 * x * x * x + 5.0 * 0.5 * x * x - 8.0 * 0.5 * x + 4.0 * 0.5;
        return 0.0;
    }

    private int index(int x, int y, int z, int width, int height) {
        return z * width * height + y * width + x;
    }

    private static class ScaleParams {
        private final double scale;

        private ScaleParams(int sourceSize, int targetSize) {
            this.scale = (double) sourceSize / targetSize;
        }

        private double sourceCoord(int targetCoord) {
            return (targetCoord + 0.5) * scale - 0.5;
        }
    }
}
