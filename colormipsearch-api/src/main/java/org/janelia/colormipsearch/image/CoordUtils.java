package org.janelia.colormipsearch.image;

public class CoordUtils {

    public static boolean intersectIsVoid(int[] min1, int[] max1, int[] min2, int[] max2) {
        for (int d = 0; d < min1.length; ++d) {
            int min = Math.max(min1[d], min2[d]);
            int max = Math.min(max1[d], max2[d]);
            if (min > max1[d] || min > max2[d] || max < min1[d] || max < min2[d]) {
                return true;
            }
        }
        return false;
    }

    public static int[] addCoords(int[] c1, int[] c2) {
        return addCoords(c1, c2, 1, new int[c1.length]);
    }

    public static int[] addCoords(int[] c1, int[] c2, int[] res) {
        return addCoords(c1, c2, 1, res);
    }

    public static int[] addCoords(int[] c1, int[] c2, int mulFactor, int[] res) {
        for (int d = 0; d < res.length; d++) {
            res[d] = c1[d] + mulFactor * c2[d];
        }
        return res;
    }

    public static int[] maxCoords(int[] shape) {
        int[] m = new int[shape.length];
        for (int d = 0; d < shape.length; d++) {
            m[d] = shape[d] - 1;
        }
        return m;
    }
}
