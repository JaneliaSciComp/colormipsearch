package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.FloatImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ShortImageArray;

/**
 * 2D squared Euclidean distance transform using the Felzenszwalb-Huttenlocher algorithm.
 * Operates on 2D slices (depth=1) of ImageArray.
 */
public class DistanceTransformAlgorithm {

    /**
     * Generate a distance transform of an RGB image after dilation with the given radius.
     * Steps: convert to grayscale intensity, dilate (max filter), then compute DT.
     *
     * @param rgbImage input RGB image (3-channel ByteImageArray, depth=1)
     * @param dilationRadius radius for max filter dilation before DT
     * @return ShortImageArray containing the distance transform values
     */
    public static ShortImageArray generateDistanceTransform(ImageArray rgbImage, int dilationRadius) {
        int width = rgbImage.getWidth();
        int height = rgbImage.getHeight();

        // Convert RGB to grayscale intensity (max of channels)
        ShortImageArray gray = new ShortImageArray(width, height, 1, 1);
        for (int pi = 0; pi < width * height; pi++) {
            int rgb = rgbImage.getIntVal(pi);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int intensity = Math.max(r, Math.max(g, b));
            gray.setIntVal(pi, intensity);
        }

        // Dilate via separable 1D max filter in X then Y
        ShortImageArray dilated = separableMaxFilter2D(gray, dilationRadius);

        // Compute distance transform
        return computeDT(dilated);
    }

    /**
     * Generate a distance transform without additional dilation.
     * Used for the target gradient in the bidirectional algorithm
     * where the input has already been dilated.
     */
    public static ShortImageArray generateDistanceTransformWithoutDilation(ImageArray rgbImage) {
        int width = rgbImage.getWidth();
        int height = rgbImage.getHeight();

        // Convert RGB to grayscale intensity
        ShortImageArray gray = new ShortImageArray(width, height, 1, 1);
        for (int pi = 0; pi < width * height; pi++) {
            int rgb = rgbImage.getIntVal(pi);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int intensity = Math.max(r, Math.max(g, b));
            gray.setIntVal(pi, intensity);
        }

        return computeDT(gray);
    }

    private static ShortImageArray separableMaxFilter2D(ShortImageArray input, int radius) {
        int width = input.getWidth();
        int height = input.getHeight();
        ShortImageArray temp = new ShortImageArray(width, height, 1, 1);
        ShortImageArray output = new ShortImageArray(width, height, 1, 1);

        // Max filter along X
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int maxVal = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = x + dx;
                    if (nx >= 0 && nx < width) {
                        maxVal = Math.max(maxVal, input.getIntPixel(nx, y, 0));
                    }
                }
                temp.setIntPixel(x, y, 0, maxVal);
            }
        }

        // Max filter along Y
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int maxVal = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int ny = y + dy;
                    if (ny >= 0 && ny < height) {
                        maxVal = Math.max(maxVal, temp.getIntPixel(x, ny, 0));
                    }
                }
                output.setIntPixel(x, y, 0, maxVal);
            }
        }

        return output;
    }

    /**
     * Compute the squared Euclidean distance transform of a grayscale image.
     * Non-zero pixels are treated as foreground (distance = 0), zero pixels get distances.
     */
    private static ShortImageArray computeDT(ShortImageArray grayInput) {
        int width = grayInput.getWidth();
        int height = grayInput.getHeight();

        // Initialize float image: foreground (intensity > 0) -> 0, background -> MAX_VALUE
        FloatImageArray floatImg = new FloatImageArray(width, height, 1, 1);
        for (int pi = 0; pi < width * height; pi++) {
            int val = grayInput.getIntVal(pi);
            floatImg.setRealVal(pi, val > 1 ? 0.0f : Float.MAX_VALUE);
        }

        float[] f = new float[Math.max(width, height)];
        float[] d = new float[Math.max(width, height)];
        int[] v = new int[Math.max(width, height)];
        float[] z = new float[Math.max(width, height) + 1];

        // Transform along columns (Y axis)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                f[y] = floatImg.getRealPixel(x, y, 0);
            }

            dt1d(f, d, v, z, height);

            for (int y = 0; y < height; y++) {
                floatImg.setRealPixel(x, y, 0, d[y]);
            }
        }

        // Transform along rows (X axis)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                f[x] = floatImg.getRealPixel(x, y, 0);
            }

            dt1d(f, d, v, z, width);

            for (int x = 0; x < width; x++) {
                floatImg.setRealPixel(x, y, 0, d[x]);
            }
        }

        // Convert back to short
        ShortImageArray result = new ShortImageArray(width, height, 1, 1);
        for (int pi = 0; pi < width * height; pi++) {
            result.setIntVal(pi, (int) floatImg.getRealVal(pi));
        }
        return result;
    }

    /**
     * 1D squared distance transform using the parabolic envelope algorithm
     * (Felzenszwalb & Huttenlocher, 2012).
     */
    private static void dt1d(float[] f, float[] d, int[] v, float[] z, int n) {
        int k = 0;
        v[0] = 0;
        z[0] = -Float.MAX_VALUE;
        z[1] = Float.MAX_VALUE;

        for (int q = 1; q < n; q++) {
            float s = ((f[q] + (float) q * q) - (f[v[k]] + (float) v[k] * v[k])) / (2 * q - 2 * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + (float) q * q) - (f[v[k]] + (float) v[k] * v[k])) / (2 * q - 2 * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.MAX_VALUE;
        }

        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
        }
    }
}
