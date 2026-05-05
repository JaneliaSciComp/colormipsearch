package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.Dimensions;

class HyperEllipsoidMask {

    private final long rx;
    private final long ry;
    private final long rz;

    HyperEllipsoidMask(int rx, int ry, int rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    boolean contains(int dx, int dy, int dz) {
        double dist = 0;

        for (int d = 0; d < 3; d++) {
            long r = Dimensions.selectDim(rx, ry, rz, d);
            int currentD = Dimensions.selectDim(dx, dy, dz, d);
            if (r == 0) {
                // radius 0 means this dimension is not used;
                // distance[d] should also be 0 (loop range is -0..0)
                if (currentD != 0) {
                    return false;
                }
            } else {
                double delta = currentD;
                dist += (delta * delta) / (r * r);
            }
        }
        return dist <= 1;
    }

}
