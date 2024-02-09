package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.LocalizableSampler;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageAccessUtils {

    public static long[] getZero(int ndims) {
        long[] z = new long[ndims];
        Arrays.fill(z, 0);
        return z;
    }

    /**
     * @param shape
     * @return the inclusive max interval bound.
     */
    public static long[] getMax(long[] shape) {
        long[] m = new long[shape.length];
        for (int d = 0; d < shape.length; d++) {
            m[d] = shape[d] - 1;
        }
        return m;
    }

    public static boolean isZero(long[] p) {
        return Arrays.stream(p).allMatch(v -> v == 0);
    }

    public static boolean isNotZero(long[] p) {
        return Arrays.stream(p).anyMatch(v -> v != 0);
    }

    public static long getMaxSize(long[] shape) {
        return Arrays.stream(shape).reduce(1, (a, d) -> a * d);
    }

    public static long getMaxSize(long[] min, long[] max) {
        long sz = 1;
        for (int d = 0; d < min.length && d < max.length; d++)
            sz *= (max[d] - min[d] + 1);
        return sz;
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> createRGBImageFromMultichannelImg(Img<UnsignedByteType> image, T backgroundPixel) {
        RandomAccessibleInterval<ARGBType> rgbImage = Converters.mergeARGB(image, ColorChannelOrder.RGB);
        Img<T> rgbImageCopy = new ArrayImgFactory<>(backgroundPixel).create(rgbImage);

        final IterableInterval<ARGBType> sourceIterable = Views.flatIterable( rgbImage );
        final IterableInterval<T> targetIterable = Views.flatIterable( rgbImageCopy );
        final Cursor<ARGBType> sourceCursor = sourceIterable.cursor();
        final Cursor<T> targetCursor = targetIterable.cursor();
        while (targetCursor.hasNext()) {
            ARGBType sourcePixel = sourceCursor.next();
            targetCursor.next().setFromRGB(
                    ARGBType.red(sourcePixel.get()),
                    ARGBType.green(sourcePixel.get()),
                    ARGBType.blue(sourcePixel.get())
            );
        }

        return new SimpleImageAccess<>(rgbImageCopy, backgroundPixel);
    }

    public static <T extends NativeType<T>> ImageAccess<T> materialize(ImageAccess<T> img) {
        return new SimpleImageAccess<>(
                materializeAsNativeImg(img, img.getBackgroundValue()),
                img.getBackgroundValue()
        );
    }

    public static <T extends NativeType<T>> Img<T> materializeAsNativeImg(RandomAccessibleInterval<T> source, T zero) {
        ImgFactory<T> imgFactory = new ArrayImgFactory<>(zero);
        Img<T> img = imgFactory.create(source);

        final IterableInterval<T> sourceIterable = Views.flatIterable( source );
        final IterableInterval<T> targetIterable = Views.flatIterable( img );
        final Cursor<T> sourceCursor = sourceIterable.cursor();
        final Cursor<T> targetCursor = targetIterable.cursor();
        while (targetCursor.hasNext()) {
            targetCursor.next().set(sourceCursor.next());
        }

        return img;
    }

    public static Stream<long[]> streamNeighborsWithinDist(int ndims, int dist, boolean includeCenter) {
        return streamNeighborsRecursive(ndims, ndims - 1, dist)
                .stream()
                .filter(pos -> includeCenter || ImageAccessUtils.isNotZero(pos));
    }

    private static List<long[]> streamNeighborsRecursive(int ndims, int currentDim, int dist) {
        if (currentDim < 0) {
            return Collections.emptyList();
        }
        return LongStream.rangeClosed(-dist, dist)
                .mapToObj(n -> {
                    long[] coord = new long[ndims];
                    coord[currentDim] = n;
                    return coord;
                })
                .flatMap(c1 -> {
                    List<long[]> prevAxisNeighbors = streamNeighborsRecursive(ndims, currentDim - 1, dist);
                    if (prevAxisNeighbors.isEmpty()) {
                        return Stream.of(c1);
                    } else {
                        return prevAxisNeighbors.stream().map(c2 -> addCoords(c1, c2));
                    }
                })
                .collect(Collectors.toList())
                ;
    }

    public static long[] addCoords(long[] c1, long[] c2) {
        long[] c = new long[c1.length];
        for (int d = 0; d < c.length; d++) {
            c[d] = c1[d] + c2[d];
        }
        return c;
    }

    public static long[] mulCoords(long[] c, int scalar) {
        long[] res = new long[c.length];
        for (int d = 0; d < c.length; d++) {
            res[d] = scalar * c[d];
        }
        return res;
    }

    public static <S, T> T fold(ImageAccess<S> image, T zero, BiFunction<T, S, T> op, BinaryOperator<T> combiner) {
        return image.stream().parallel().reduce(zero, op, combiner);
    }

    public static <T> Stream<LocalizableSampler<T>> stream(Cursor<T> cursor, boolean parallel) {
        final CursorLocalizableSampler<T> currentCursor = new CursorLocalizableSampler<>(cursor.copy());
        currentCursor.fwd();
        Spliterator<LocalizableSampler<T>> spliterator = new Spliterators.AbstractSpliterator<LocalizableSampler<T>>(
                Long.MAX_VALUE, 0) {

            @Override
            public boolean tryAdvance(Consumer<? super LocalizableSampler<T>> action) {
                action.accept(currentCursor);
                if (!currentCursor.hasNext()) {
                    return false;
                }
                // advance cursor
                currentCursor.fwd();
                return true;
            }
        };
        return StreamSupport.stream(spliterator, parallel);
    }
}
