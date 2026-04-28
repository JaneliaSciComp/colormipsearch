package org.janelia.colormipsearch.image.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.ImageArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * N-dimensional max filter (morphological dilation) operating on ImageArray.
 */
public class MaxFilterAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(MaxFilterAlgorithm.class);

    interface MaxPixelValue {
        int maxOf(int p1, int p2);
    }

    public static ImageArray maxFilterGray(ImageArray input, int xRadius, int yRadius, int zRadius) {
        return maxFilter(input, xRadius, yRadius, zRadius, MaxFilterAlgorithm::maxGrayValue);
    }

    public static ImageArray maxFilterRGB(ImageArray input, int xRadius, int yRadius, int zRadius) {
        return maxFilter(input, xRadius, yRadius, zRadius, MaxFilterAlgorithm::maxRGBValue);
    }

    public static ImageArray maxFilterGrayMT(ImageArray input,
                                             int xRadius, int yRadius, int zRadius,
                                             ExecutorService executorService) {
        return maxFilterMT(input, xRadius, yRadius, zRadius, MaxFilterAlgorithm::maxGrayValue, executorService);
    }

    public static ImageArray maxFilterRGBMT(ImageArray input,
                                            int xRadius, int yRadius, int zRadius,
                                            ExecutorService executorService) {
        return maxFilterMT(input, xRadius, yRadius, zRadius, MaxFilterAlgorithm::maxRGBValue, executorService);
    }

    /**
     * 2D RGB max filter in X and Y (zRadius=0), creating a new image.
     */
    public static ImageArray rgbMaxFilter2D(ImageArray input, int radius) {
        return maxFilter(input, radius, radius, 0, MaxFilterAlgorithm::maxRGBValue);
    }

    private static int maxRGBValue(int p1, int p2) {
        int r1 = (p1 >> 16) & 0xFF;
        int g1 = (p1 >> 8) & 0xFF;
        int b1 = p1 & 0xFF;
        int r2 = (p2 >> 16) & 0xFF;
        int g2 = (p2 >> 8) & 0xFF;
        int b2 = p2 & 0xFF;
        return (Math.max(r1, r2) << 16) | (Math.max(g1, g2) << 8) | Math.max(b1, b2);
    }

    private static int maxGrayValue(int p1, int p2) {
        return Math.max(p1, p2);
    }

    private static ImageArray maxFilterMT(ImageArray input,
                                          int xRadius, int yRadius, int zRadius,
                                          MaxPixelValue maxPixelValueGetter,
                                          ExecutorService executorService) {
        ImageArray output = input.getFactory().create(input.getWidth(), input.getHeight(), input.getDepth(), input.getChannels());
        HyperEllipsoidMask kernel = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        List<Callable<Void>> dilationTasks = new ArrayList<>();
        for (int zi = 0; zi < depth; zi++) {
            int z = zi;
            dilationTasks.add(() -> {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int maxIntensity = getMaxIntensity(input, x, y, z,
                                xRadius, yRadius, zRadius,
                                width, height, depth,
                                kernel, maxPixelValueGetter);
                        output.setIntPixel(x, y, z, maxIntensity);
                    }
                }
                return null;
            });
        }
        try {
            executorService.invokeAll(dilationTasks);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return output;
    }

    private static ImageArray maxFilter(ImageArray input,
                                        int xRadius, int yRadius, int zRadius,
                                        MaxPixelValue maxPixelValueGetter) {
        long startTime = System.currentTimeMillis();
        ImageArray output = input.getFactory().create(input.getWidth(), input.getHeight(), input.getDepth(), input.getChannels());
        HyperEllipsoidMask kernel = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
        int width = input.getWidth();
        int height = input.getHeight();
        int depth = input.getDepth();
        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int maxIntensity = getMaxIntensity(input, x, y, z,
                            xRadius, yRadius, zRadius,
                            width, height, depth,
                            kernel, maxPixelValueGetter);
                    output.setIntPixel(x, y, z, maxIntensity);
                }
            }
        }
        LOG.debug("Max filter {}x{}x{} completed in {}ms", xRadius, yRadius, zRadius, System.currentTimeMillis() - startTime);
        return output;
    }

    private static int getMaxIntensity(ImageArray input,
                                       int cx, int cy, int cz,
                                       int xRadius, int yRadius, int zRadius,
                                       int width, int height, int depth,
                                       HyperEllipsoidMask kernel,
                                       MaxPixelValue maxPixelValueGetter) {
        int maxVal = 0;
        for (int dz = -zRadius; dz <= zRadius; dz++) {
            int nz = cz + dz;
            if (nz < 0 || nz >= depth) continue;
            for (int dy = -yRadius; dy <= yRadius; dy++) {
                int ny = cy + dy;
                if (ny < 0 || ny >= height) continue;
                for (int dx = -xRadius; dx <= xRadius; dx++) {
                    int nx = cx + dx;
                    if (nx < 0 || nx >= width) continue;
                    if (kernel.contains(Math.abs(dx), Math.abs(dy), Math.abs(dz))) {
                        int val = input.getIntPixel(nx, ny, nz);
                        maxVal = maxPixelValueGetter.maxOf(maxVal, val);
                    }
                }
            }
        }
        return maxVal;
    }
}
