package org.janelia.colormipsearch.image;

import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.NumericType;

public class ImageDraw {

    public static < T extends NumericType<T> > void draw3dLine(RandomAccess<T> img,
                                                               int w, int h, int d,
                                                               double x1, double y1, double z1, double r1,
                                                               double x2, double y2, double z2, double r2,
                                                               double zratio)
    {
        double a = Math.abs(x2 - x1), b = Math.abs(y2 - y1), c = Math.abs(z2 - z1);
        int pattern;
        if(a >= b && a >= c)
            pattern = 0;
        else if(b >= a && b >= c)
            pattern = 1;
        else // c >= a && c >= b)
            pattern = 2;

        if (r1 <= 0.0) r1 = 0.0;
        if (r2 <= 0.0) r2 = 0.0;
        int ir1 = r1 > 0.0 ? (int)Math.ceil(r1) : 0;
        int ir2 = r2 > 0.0 ? (int)Math.ceil(r2) : 0;

        double stx, sty, stz;
        double edx, edy, edz;
        double dx, dy, dz, dr;
        int ix, iy, iz;
        int iteration;
        int sign;

        switch (pattern) {
            case 0:
                sign = x2 > x1 ? 1 : -1; // dx
                dy = (y2 - y1) / (x2 - x1);
                dz = (z2 - z1) / (x2 - x1);
                dr = (r2 - r1) / (x2 - x1);
                stx = (int)x1;
                sty = (stx-x1)*dy + y1;
                stz = (stx-x1)*dz + z1;
                edx = (int)x2;
                iteration = (int)Math.abs(edx - stx);
                for (int i = 0; i <= iteration; i++) {
                    ix = (int)(stx + i*sign);
                    iy = (int)(sty + dy*i*sign);
                    iz = (int)(stz + dz*i*sign);
                    if (ir1 == 0 && ir2 == 0) {
                        if (ix >= 0 && ix < w && iy >= 0 && iy < h && iz >= 0 && iz < d)
                            img.setPositionAndGet(ix, iy, iz).setOne();
                    } else {
                        draw3dSphere(img, w, h, d, ix, iy, iz, r1+dr*i*sign, zratio);
                    }
                }
                break;
            case 1:
                sign = y2 > y1 ? 1 : -1; // dy
                dz = (z2 - z1) / (y2 - y1);
                dx = (x2 - x1) / (y2 - y1);
                dr = (r2 - r1) / (y2 - y1);
                sty = (int)y1;
                stz = (sty-y1)*dz + z1;
                stx = (sty-y1)*dx + x1;
                edy = (int)y2;
                iteration = (int)Math.abs(edy - sty);
                for (int i = 0; i <= iteration; i++) {
                    iy = (int)(sty + i*sign);
                    iz = (int)(stz + dz*i*sign);
                    ix = (int)(stx + dx*i*sign);
                    if (ir1 == 0 && ir2 == 0) {
                        if (ix >= 0 && ix < w && iy >= 0 && iy < h && iz >= 0 && iz < d)
                            img.setPositionAndGet(ix, iy, iz).setOne();
                    } else {
                        draw3dSphere(img, w, h, d, ix, iy, iz, r1+dr*i*sign, zratio);
                    }
                }
                break;
            case 2:
                sign = z2 > z1 ? 1 : -1; // dz
                dx = (x2 - x1) / (z2 - z1);
                dy = (y2 - y1) / (z2 - z1);
                dr = (r2 - r1) / (z2 - z1);
                stz = (int)z1;
                stx = (stz-z1)*dx + x1;
                sty = (stz-z1)*dy + y1;
                edz = (int)z2;
                iteration = (int)(Math.abs(edz - stz));
                for (int i = 0; i <= iteration; i++) {
                    iz = (int)(stz + i*sign);
                    ix = (int)(stx + dx*i*sign);
                    iy = (int)(sty + dy*i*sign);
                    if (ir1 == 0 && ir2 == 0) {
                        if (ix >= 0 && ix < w && iy >= 0 && iy < h && iz >= 0 && iz < d)
                            img.setPositionAndGet(ix, iy, iz).setOne();
                    } else {
                        draw3dSphere(img, w, h, d, ix, iy, iz, r1+dr*i*sign, zratio);
                    }
                }
                break;
        }
    }

    private static < T extends NumericType<T> > void draw3dSphere(RandomAccess<T> img,
                                                                  int w, int h, int d,
                                                                  int x, int y, int z,
                                                                  double r, double zratio)
    {

        int ir = (int)Math.ceil(r);
        int izr = (int)Math.ceil(r/zratio);
        for (int dz = -izr; dz <= izr; dz++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dx = -ir; dx <= ir; dx++) {
                    int xx = x + dx;
                    int yy = y + dy;
                    int zz = z + dz;
                    double dd = dx*dx + dy*dy + dz*zratio*dz*zratio;
                    if (dd <= r*r) {
                        if (xx >= 0 && xx < w && yy >= 0 && yy < h && zz >= 0 && zz < d)
                            img.setPositionAndGet(xx, yy, zz).setOne();
                    }
                }
            }
        }
    }

}
