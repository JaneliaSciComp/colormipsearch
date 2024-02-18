package org.janelia.colormipsearch.image;

import net.imglib2.Localizable;

public class CoordUtils {
    public static long[] addCoords(long[] c1, long[] c2) {
        return addCoords(c1, c2, new long[c1.length]);
    }

    public static long[] addCoords(long[] c1, int[] c2, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c1[d] + c2[d];
        }
        return res;
    }

    public static long[] addCoords(long[] c1, long[] c2, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c1[d] + c2[d];
        }
        return res;
    }

    public static long[] addCoords(long[] c1, Localizable c2, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c1[d] + c2.getLongPosition(d);
        }
        return res;
    }

    public static long[] addCoord(long[] c, long position, int axis) {
        c[axis] += position;
        return c;
    }

    public static void addCoord(long[] c, long position, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c[d] + position;
        }
    }

    public static long[] mulCoords(long[] c, int scalar) {
        return mulCoords(c, scalar, new long[c.length]);
    }

    public static long[] mulCoords(long[] c, int scalar, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = scalar * c[d];
        }
        return res;
    }

    public static long[] setCoords(Localizable c, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c.getLongPosition(d);
        }
        return res;
    }

    public static long[] setCoords(int[] c, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c[d];
        }
        return res;
    }

    public static long[] setCoords(long[] c, long[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c[d];
        }
        return res;
    }

    public static long[] updateCoord(long[] c, long position, int axis) {
        c[axis] = position;
        return c;
    }

    public static void intersect(long[] minA, long[] maxA,
                                 long[] minB, long[] maxB,
                                 long[] minRes, long[] maxRes,
                                 int startAxis, int endAxis) {
        for (int d = startAxis; d <= endAxis; ++d) {
            minRes[d] = Math.max(minA[d], minB[d]);
            maxRes[d] = Math.min(maxA[d], maxB[d]);
        }
    }
}
