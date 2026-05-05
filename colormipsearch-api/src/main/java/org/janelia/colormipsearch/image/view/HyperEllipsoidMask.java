package org.janelia.colormipsearch.image;

public class HyperEllipsoidMask {

    private final int rx;
    private final int ry;
    private final int rz;

    public HyperEllipsoidMask(int rx, int ry, int rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    public boolean contains(int dx, int dy, int dz) {
        double dist = 0;

        for (int d = 0; d < 3; d++) {
            int r = Dimensions.selectDim(rx, ry, rz, d);
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
