package org.janelia.colormipsearch.image.algorithms;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class Scale3DAlgorithm {

    static final double ALPHA = 0.5; // Catmull-Rom interpolation

    public static <T extends IntegerType<T> & NativeType<T>> Img<T> scale3DImage(RandomAccessibleInterval<T> img,
                                                                                 int dstWidth, int dstHeight, int dstDepth,
                                                                                 T pxType) {
        int srcWidth = (int) img.dimension(0);
        int srcHeight = (int) img.dimension(1);
        int srcDepth = (int) img.dimension(2);

        double srcCenterX = (double) srcWidth / 2.0;
        double srcCenterY = (double) srcHeight / 2.0;
        double srcCenterZ = (double) srcDepth / 2.0;
        double dstCenterX = (double) dstWidth / 2.0;
        double dstCenterY = (double) dstHeight / 2.0;
        double dstCenterZ = (double) dstDepth / 2.0;
        double xScale = (double) dstWidth / srcWidth;
        double yScale = (double) dstHeight / srcHeight;
        double zScale = (double) dstDepth / srcDepth;

        if (dstWidth != srcWidth) dstCenterX += xScale / 4.0;
        if (dstHeight != srcHeight) dstCenterY += yScale / 4.0;
        if (dstDepth != srcDepth) dstCenterZ += zScale / 4.0;

        RandomAccess<T> sourceImgRA = img.randomAccess();

        long[] newDimensions = new long[]{dstWidth, dstHeight, dstDepth};
        long[] maxDimensions = new long[]{dstWidth > srcWidth ? dstWidth : srcWidth, dstHeight > srcHeight ? dstHeight : srcHeight, dstDepth};
        long[] tmpDimensions = new long[]{srcWidth, dstHeight, dstDepth};
        ArrayImgFactory<FloatType> factory = new ArrayImgFactory<>(new FloatType());
        Img<FloatType> scaledImg = factory.create(maxDimensions);
        RandomAccess<FloatType> scaledRA = scaledImg.randomAccess();
        Img<FloatType> scaledImg2 = factory.create(tmpDimensions);
        RandomAccess<FloatType> scaledRA2 = scaledImg2.randomAccess();

        int maxval = (int) sourceImgRA.get().getMaxValue();

        double xs, ys, zs;
        for (int z = 0; z <= dstDepth - 1; z++) {
            zs = (z - dstCenterZ) / zScale + srcCenterZ;
            for (int y = 0; y <= srcHeight - 1; y++) {
                for (int x = 0; x <= srcWidth - 1; x++) {
                    double value = getCubicInterpolatedPixelZ(x, y, zs, srcWidth, srcHeight, srcDepth, sourceImgRA);
                    scaledRA.setPositionAndGet(x, y, z).setReal(value);
                }
            }
        }

        for (int z = 0; z <= dstDepth - 1; z++) {
            for (int y = 0; y <= dstHeight - 1; y++) {
                ys = (y - dstCenterY) / yScale + srcCenterY;
                for (int x = 0; x <= srcWidth - 1; x++) {
                    double value = getCubicInterpolatedPixelY(x, ys, z, srcWidth, srcHeight, dstDepth, scaledRA);
                    scaledRA2.setPositionAndGet(x, y, z).setReal(value);
                }
            }
        }

        for (int z = 0; z <= dstDepth - 1; z++) {
            for (int y = 0; y <= dstHeight - 1; y++) {
                for (int x = 0; x <= dstWidth - 1; x++) {
                    xs = (x - dstCenterX) / xScale + srcCenterX;
                    double value = getCubicInterpolatedPixelX(xs, y, z, srcWidth, dstHeight, dstDepth, scaledRA2);
                    scaledRA.setPositionAndGet(x, y, z).setReal(value);
                }
            }
        }

        Img<T> finalImg = new ArrayImgFactory<>(pxType).create(newDimensions);
        long[] min = new long[]{0, 0, 0}; // Starting coordinates of the rectangle
        long[] max = new long[]{newDimensions[0] - 1, newDimensions[1] - 1, newDimensions[2] - 1}; // Ending coordinates of the rectangle

        // Create a view on the specified interval
        IntervalView<FloatType> srcRegion = Views.interval(scaledImg, min, max);

        Cursor<FloatType> srcCursor = srcRegion.cursor();
        Cursor<T> destCursor = finalImg.cursor();
        while (srcCursor.hasNext() && destCursor.hasNext()) {
            int value = (int) (srcCursor.next().getRealFloat() + 0.5f);
            if (value < 0) value = 0;
            if (value > maxval) value = maxval;
            destCursor.next().setInteger(value);
        }

        return finalImg;
    }

    private static <T extends RealType<T>> double getCubicInterpolatedPixelZ(int x0, int y0, double z0, int w, int h, int d, RandomAccess<T> img) {
        int t0 = (int) Math.floor(z0);    //use floor to handle negative coordinates too
        if (t0 <= 0 || t0 >= d - 2)
            return getLinearInterpolatedPixelZ(x0, y0, z0, w, h, d, img);

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int t = t0 - 1 + i;
            p = p + img.setPositionAndGet(x0, y0, t).getRealDouble() * cubic(z0 - t);
        }

        return p;
    }

    private static <T extends RealType<T>> double getLinearInterpolatedPixelZ(int ix, int iy, double z, int w, int h, int d, RandomAccess<T> img) {
        if (ix >= -1 && ix < w && iy >= -1 && iy < h && z >= -1 && z < d) {
            if (z < 0.0) z = 0.0;
            if (z >= d - 1.0) z = d - 1.001;

            int iz = (int) z;
            double dz = z - iz;

            double c0 = img.setPositionAndGet(ix, iy, iz).getRealDouble();
            double c1 = img.setPositionAndGet(ix, iy, iz + 1).getRealDouble();
            double c = c0 * (1 - dz) + c1 * dz;

            return c;
        } else
            return 0.0;
    }

    private static <T extends RealType<T>> double getCubicInterpolatedPixelY(int x0, double y0, int z0, int w, int h, int d, RandomAccess<T> img) {
        int v0 = (int) Math.floor(y0);    //use floor to handle negative coordinates too
        if (v0 <= 0 || v0 >= h - 2)
            return getLinearInterpolatedPixelY(x0, y0, z0, w, h, d, img);

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int v = v0 - 1 + i;
            p = p + img.setPositionAndGet(x0, v, z0).getRealDouble() * cubic(y0 - v);
        }

        return p;
    }

    private static <T extends RealType<T>> double getLinearInterpolatedPixelY(int ix, double y, int iz, int w, int h, int d, RandomAccess<T> img) {
        if (ix >= -1 && ix < w && y >= -1 && y < h && iz >= -1 && iz < d) {
            if (y < 0.0) y = 0.0;
            if (y >= h - 1.0) y = h - 1.001;

            int iy = (int) y;
            double dy = y - iy;
            double c0 = img.setPositionAndGet(ix, iy, iz).getRealDouble();
            double c1 = img.setPositionAndGet(ix, iy + 1, iz).getRealDouble();
            double c = c0 * (1 - dy) + c1 * dy;

            return c;
        } else
            return 0.0;
    }

    public static <T extends RealType<T>> double getCubicInterpolatedPixelX(double x0, int y0, int z0, int w, int h, int d, RandomAccess<T> img) {
        int u0 = (int) Math.floor(x0);    //use floor to handle negative coordinates too
        if (u0 <= 0 || u0 >= w - 2)
            return getLinearInterpolatedPixelX(x0, y0, z0, w, h, d, img);

        double p = 0;
        for (int i = 0; i <= 3; i++) {
            int u = u0 - 1 + i;
            p = p + img.setPositionAndGet(u, y0, z0).getRealDouble() * cubic(x0 - u);
        }

        return p;
    }

    private static <T extends RealType<T>> double getLinearInterpolatedPixelX(double x, int iy, int iz, int w, int h, int d, RandomAccess<T> img) {
        if (x >= -1 && x < w && iy >= -1 && iy < h && iz >= -1 && iz < d) {
            if (x < 0.0) x = 0.0;
            if (x >= w - 1.0) x = w - 1.001;

            int ix = (int) x;
            double dx = x - ix;
            double c0 = img.setPositionAndGet(ix, iy, iz).getRealDouble();
            double c1 = img.setPositionAndGet(ix + 1, iy, iz).getRealDouble();
            double c = c0 * (1 - dx) + c1 * dx;

            return c;
        } else
            return 0.0;
    }

    private static double cubic(double x) {
        if (x < 0.0) x = -x;
        double z = 0.0;
        if (x < 1.0)
            z = x * x * (x * (-ALPHA + 2.0) + (ALPHA - 3.0)) + 1.0;
        else if (x < 2.0)
            z = -ALPHA * x * x * x + 5.0 * ALPHA * x * x - 8.0 * ALPHA * x + 4.0 * ALPHA;
        return z;
    }

}
