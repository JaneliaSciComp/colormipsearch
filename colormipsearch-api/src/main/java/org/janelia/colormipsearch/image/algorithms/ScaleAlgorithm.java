package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.MultiChannelByteImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;

public class ScaleAlgorithm {

    private static final double ALPHA = 0.5;

    /**
     * Rescale a volume to target dimensions using separable bicubic interpolation.
     * Matches the bak version's ScaleTransformRandomAccess behavior: applies cubic
     * interpolation one axis at a time (Z, then Y, then X), materializing after each pass.
     */
    public static ImageArray scaleVolume(ImageArray input, int targetW, int targetH, int targetD, ImageArrayFactory factory) {
        int srcW = input.getWidth();
        int srcH = input.getHeight();
        int srcD = input.getDepth();
        if (srcW == targetW && srcH == targetH && srcD == targetD) {
            return input;
        }
        // Separable: scale Z first, then Y, then X (reverse order like the bak)
        ImageArray current = input;
        int curW = srcW, curH = srcH, curD = srcD;

        // Scale along Z
        if (curD != targetD) {
            WriteableImageArray scaled = factory.create(curW, curH, targetD);
            double scaleFactor = (double) curD / targetD;
            for (int z = 0; z < targetD; z++) {
                double sz = z * scaleFactor;
                for (int y = 0; y < curH; y++) {
                    for (int x = 0; x < curW; x++) {
                        int val = interpolate1D(current, x, y, sz, 2, curW, curH, curD, scaleFactor);
                        scaled.setPackedIntValAtCoords(x, y, z, val);
                    }
                }
            }
            current = scaled;
            curD = targetD;
        }

        // Scale along Y
        if (curH != targetH) {
            WriteableImageArray scaled = factory.create(curW, targetH, curD);
            double scaleFactor = (double) curH / targetH;
            for (int z = 0; z < curD; z++) {
                for (int y = 0; y < targetH; y++) {
                    double sy = y * scaleFactor;
                    for (int x = 0; x < curW; x++) {
                        int val = interpolate1D(current, x, sy, z, 1, curW, curH, curD, scaleFactor);
                        scaled.setPackedIntValAtCoords(x, y, z, val);
                    }
                }
            }
            current = scaled;
            curH = targetH;
        }

        // Scale along X
        if (curW != targetW) {
            WriteableImageArray scaled = factory.create(targetW, curH, curD);
            double scaleFactor = (double) curW / targetW;
            for (int z = 0; z < curD; z++) {
                for (int y = 0; y < curH; y++) {
                    for (int x = 0; x < targetW; x++) {
                        double sx = x * scaleFactor;
                        int val = interpolate1D(current, sx, y, z, 0, curW, curH, curD, scaleFactor);
                        scaled.setPackedIntValAtCoords(x, y, z, val);
                    }
                }
            }
            current = scaled;
        }

        return current;
    }

    /**
     * Bicubic interpolation along a single axis.
     * Coordinates are integer for the non-interpolated axes and real for the interpolated axis.
     */
    private static int interpolate1D(ImageArray img,
                                     double x, double y, double z,
                                     int axis,
                                     int w, int h, int d,
                                     double scaleFactor) {
        double realPos;
        int ix = (int) x, iy = (int) y, iz = (int) z;
        switch (axis) {
            case 0: realPos = x; break;
            case 1: realPos = y; break;
            case 2: realPos = z; break;
            default: throw new IllegalArgumentException("axis must be 0, 1, or 2");
        }
        int t0 = (int) Math.floor(realPos);
        double interpolated = 0;
        for (int i = 0; i <= 3; i++) {
            int t = t0 - 1 + i;
            double v;
            int dimSize = (axis == 0) ? w : (axis == 1) ? h : d;
            if (t >= 0 && t < dimSize) {
                switch (axis) {
                    case 0: v = img.getPackedIntValAtCoords(t, iy, iz); break;
                    case 1: v = img.getPackedIntValAtCoords(ix, t, iz); break;
                    case 2: v = img.getPackedIntValAtCoords(ix, iy, t); break;
                    default: v = 0;
                }
            } else {
                v = 0;
            }
            interpolated += v * cubic(realPos - t);
        }
        int pxValue = (int) (interpolated + 0.5);
        if (pxValue < 0) pxValue = 0;
        if (pxValue > 65535) pxValue = 65535;
        return pxValue;
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
