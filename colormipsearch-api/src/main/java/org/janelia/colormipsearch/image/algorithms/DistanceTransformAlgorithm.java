package org.janelia.colormipsearch.image.algorithms;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class DistanceTransformAlgorithm {

    public static <T extends RGBPixelType<T>> Img<UnsignedShortType> generateDistanceTransform(RandomAccessibleInterval<? extends RGBPixelType<?>> input, int radius) {
        UnsignedShortType grayPxType = new UnsignedShortType();
        @SuppressWarnings("unchecked")
        Img<UnsignedShortType> input16 = ImageAccessUtils.materializeAsNativeImg(
                (RandomAccessibleInterval<T>) input,
                null,
                grayPxType,
                ImageTransforms.getRGBToIntensity(false)
        );
        Img<UnsignedShortType> temp = input16.factory().create(input16);
        MaxFilterAlgorithm.maxFilterInX(input16, temp, radius);
        MaxFilterAlgorithm.maxFilterInY(temp, input16, radius);
        Img<FloatType> dilatedInput32 = ImageAccessUtils.materializeAsNativeImg(
                input16,
                null,
                new FloatType(),
                (s, t) -> t.set(s.getRealFloat())
        );
        Cursor<FloatType> cursor = dilatedInput32.cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            float val = cursor.get().get();
            if (val > 1)
                cursor.get().set(0.0f);
            else
                cursor.get().set(Float.MAX_VALUE);
        }
        dt(dilatedInput32);
        return ImageAccessUtils.materializeAsNativeImg(
                dilatedInput32,
                null,
                grayPxType,
                (s, t) -> t.set((short)s.get())
        );
    }

    public static <T extends RGBPixelType<T>> Img<UnsignedShortType> generateDistanceTransformWithoutDilation(RandomAccessibleInterval<? extends RGBPixelType<?>> input) {
        @SuppressWarnings("unchecked")
        Img<FloatType> dilatedInput32 = ImageAccessUtils.materializeAsNativeImg(
                (RandomAccessibleInterval<T>) input,
                null,
                new FloatType(),
                ImageTransforms.getRGBToIntensity(false)
        );
        Cursor<FloatType> cursor = dilatedInput32.cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            float val = cursor.get().get();
            if (val > 1)
                cursor.get().set(0.0f);
            else
                cursor.get().set(Float.MAX_VALUE);
        }

        dt(dilatedInput32);
        return ImageAccessUtils.materializeAsNativeImg(
                dilatedInput32,
                null,
                new UnsignedShortType(),
                (s, t) -> t.set((short)s.get())
        );
    }

    /* dt of 2d function using squared distance */
    private static void dt(Img<FloatType> im) {
        final int width = (int)im.dimension(0);
        final int height = (int)im.dimension(1);
        final float[] f = new float[Math.max(width,height)];
        final float[] d = new float[Math.max(width,height)];
        final int[] v = new int[Math.max(width,height)];
        final float[] z = new float[Math.max(width,height)+1];
        int n = 0;

        RandomAccess<FloatType> ra = im.randomAccess();

        // transform along columns
        for (int x = 0; x < width; x++) {
            ra.setPosition(x, 0);
            for (int y = 0; y < height; y++) {
                ra.setPosition(y, 1);
                f[y] = ra.get().get();
            }

            int k = 0;
            n = height;
            v[0] = 0;
            z[0] = -Float.MAX_VALUE;
            z[1] = Float.MAX_VALUE;
            for (int q = 1; q <= n-1; q++) {
                float s  = ((f[q]+q*q)-(f[v[k]]+v[k]*v[k]))/(2*q-2*v[k]);
                while (s <= z[k]) {
                    k--;
                    s  = ((f[q]+q*q)-(f[v[k]]+v[k]*v[k]))/(2*q-2*v[k]);
                }
                k++;
                v[k] = q;
                z[k] = s;
                z[k+1] = Float.MAX_VALUE;
            }
            k = 0;
            for (int q = 0; q <= n-1; q++) {
                while (z[k+1] < q)
                    k++;
                d[q] = (q-v[k])*(q-v[k]) + f[v[k]];
            }
            for (int y = 0; y < height; y++) {
                ra.setPosition(y, 1);
                ra.get().set(d[y]);
            }
        }

        // transform along rows
        for (int y = 0; y < height; y++) {
            ra.setPosition(y, 1);
            for (int x = 0; x < width; x++) {
                ra.setPosition(x, 0);
                f[x] = ra.get().get();
            }

            int k = 0;
            n = width;
            v[0] = 0;
            z[0] = -Float.MAX_VALUE;
            z[1] = Float.MAX_VALUE;
            for (int q = 1; q <= n-1; q++) {
                float s  = ((f[q]+q*q)-(f[v[k]]+v[k]*v[k]))/(2*q-2*v[k]);
                while (s <= z[k]) {
                    k--;
                    s  = ((f[q]+q*q)-(f[v[k]]+v[k]*v[k]))/(2*q-2*v[k]);
                }
                k++;
                v[k] = q;
                z[k] = s;
                z[k+1] = Float.MAX_VALUE;;
            }
            k = 0;
            for (int q = 0; q <= n-1; q++) {
                while (z[k+1] < q)
                    k++;
                d[q] = (q-v[k])*(q-v[k]) + f[v[k]];
            }

            for (int x = 0; x < width; x++) {
                ra.setPosition(x, 0);
                ra.get().set(d[x]);
            }
        }

        Cursor<FloatType> cursor = im.cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            float val = cursor.get().get();
            cursor.get().set((float)Math.sqrt(val));
        }
    }

}
