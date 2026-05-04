package org.janelia.colormipsearch.image;

/**
 * Utility for drawing lines and shapes into ImageArray volumes.
 */
public class ImageDraw {

    public static void draw3dLine(WriteableImageArray img,
                                  double x1, double y1, double z1, int r1,
                                  double x2, double y2, double z2, int r2,
                                  double zratio,
                                  int fgPx) {
        double a = Math.abs(x2 - x1), b = Math.abs(y2 - y1), c = Math.abs(z2 - z1);
        int pattern;
        if (a >= b && a >= c)
            pattern = 0;
        else if (b >= a && b >= c)
            pattern = 1;
        else
            pattern = 2;

        int ir1 = Math.abs(r1);
        int ir2 = Math.abs(r2);

        double dx, dy, dz, dr;
        int ix, iy, iz;
        int iteration;
        int sign;

        switch (pattern) {
            case 0:
                sign = x2 > x1 ? 1 : -1;
                dy = (y2 - y1) / (x2 - x1);
                dz = (z2 - z1) / (x2 - x1);
                dr = (r2 - r1) / (x2 - x1);
                double stx = (int) x1;
                double sty = (stx - x1) * dy + y1;
                double stz = (stx - x1) * dz + z1;
                double edx = (int) x2;
                iteration = (int) Math.abs(edx - stx);
                for (int i = 0; i <= iteration; i++) {
                    ix = (int) (stx + i * sign);
                    iy = (int) (sty + dy * i * sign);
                    iz = (int) (stz + dz * i * sign);
                    if (ir1 == 0 && ir2 == 0) {
                        setPixelSafe(img, ix, iy, iz, fgPx);
                    } else {
                        draw3dSphere(img, ix, iy, iz, r1 + dr * i * sign, zratio, fgPx);
                    }
                }
                break;
            case 1:
                sign = y2 > y1 ? 1 : -1;
                dz = (z2 - z1) / (y2 - y1);
                dx = (x2 - x1) / (y2 - y1);
                dr = (r2 - r1) / (y2 - y1);
                sty = (int) y1;
                stz = (sty - y1) * dz + z1;
                stx = (sty - y1) * dx + x1;
                double edy = (int) y2;
                iteration = (int) Math.abs(edy - sty);
                for (int i = 0; i <= iteration; i++) {
                    iy = (int) (sty + i * sign);
                    iz = (int) (stz + dz * i * sign);
                    ix = (int) (stx + dx * i * sign);
                    if (ir1 == 0 && ir2 == 0) {
                        setPixelSafe(img, ix, iy, iz, fgPx);
                    } else {
                        draw3dSphere(img, ix, iy, iz, r1 + dr * i * sign, zratio, fgPx);
                    }
                }
                break;
            case 2:
                sign = z2 > z1 ? 1 : -1;
                dx = (x2 - x1) / (z2 - z1);
                dy = (y2 - y1) / (z2 - z1);
                dr = (r2 - r1) / (z2 - z1);
                stz = (int) z1;
                stx = (stz - z1) * dx + x1;
                sty = (stz - z1) * dy + y1;
                double edz = (int) z2;
                iteration = (int) (Math.abs(edz - stz));
                for (int i = 0; i <= iteration; i++) {
                    iz = (int) (stz + i * sign);
                    ix = (int) (stx + dx * i * sign);
                    iy = (int) (sty + dy * i * sign);
                    if (ir1 == 0 && ir2 == 0) {
                        setPixelSafe(img, ix, iy, iz, fgPx);
                    } else {
                        draw3dSphere(img, ix, iy, iz, r1 + dr * i * sign, zratio, fgPx);
                    }
                }
                break;
        }
    }

    private static void setPixelSafe(WriteableImageArray img, int x, int y, int z, int val) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight() && z >= 0 && z < img.getDepth()) {
            img.setPackedIntValAtCoords(x, y, z, val);
        }
    }

    private static void draw3dSphere(WriteableImageArray img,
                                     int x, int y, int z,
                                     double r, double zratio,
                                     int fgPx) {
        int ir = (int) Math.ceil(r);
        int izr = (int) Math.ceil(r / zratio);
        for (int ddz = -izr; ddz <= izr; ddz++) {
            for (int ddy = -ir; ddy <= ir; ddy++) {
                for (int ddx = -ir; ddx <= ir; ddx++) {
                    int xx = x + ddx;
                    int yy = y + ddy;
                    int zz = z + ddz;
                    double dd = ddx * ddx + ddy * ddy + ddz * zratio * ddz * zratio;
                    if (dd <= r * r) {
                        setPixelSafe(img, xx, yy, zz, fgPx);
                    }
                }
            }
        }
    }
}
