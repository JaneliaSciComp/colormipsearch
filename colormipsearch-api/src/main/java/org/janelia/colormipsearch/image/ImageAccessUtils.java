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
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.LocalizableSampler;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageAccessUtils {

    public static class ContrastStretchingParams {
        public final int threshold;
        public final int minIntensity;
        public final int maxIntensity;
        public final int defaultMinIntensity;

        ContrastStretchingParams(int threshold, int minIntensity, int maxIntensity) {
            this.threshold = threshold;
            this.minIntensity = minIntensity;
            this.maxIntensity = maxIntensity;
            this.defaultMinIntensity = 0;
        }
    }
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

    public static long[] getMax(int... shape) {
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

    public static <T extends RGBPixelType<T>> Img<T> createRGBImageFromMultichannelImg(Img<UnsignedByteType> image, T backgroundPixel) {
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
        return rgbImageCopy;
    }

    public static boolean sameShape(RandomAccessibleInterval<?> ref, RandomAccessibleInterval<?> img) {
        long[] refShape = ref.dimensionsAsLongArray();
        long[] imgShape = img.dimensionsAsLongArray();
        if (refShape.length != imgShape.length)
            return false;
        for (int d = 0; d < refShape.length; d++) {
            if (refShape[d] != imgShape[d])
                return false;
        }
        return true;
    }

    public static boolean differentShape(RandomAccessibleInterval<?> ref, RandomAccessibleInterval<?> img) {
        return !sameShape(ref, img);
    }

    public static <T extends NativeType<T>> Img<T> materializeAsNativeImg(RandomAccessibleInterval<T> source, Interval interval, T pxType) {
        if (source instanceof Img && interval == null) {
            return (Img<T>) source;
        }
        ImgFactory<T> imgFactory = new ArrayImgFactory<>(pxType);
        Img<T> img = imgFactory.create(interval == null ? source : interval);
        final IterableInterval<T> sourceIterable = Views.flatIterable(
                interval != null ? Views.interval(source, interval) : source
        );
        final IterableInterval<T> targetIterable = Views.flatIterable(img);
        final Cursor<T> sourceCursor = sourceIterable.cursor();
        final Cursor<T> targetCursor = targetIterable.cursor();
        while (sourceCursor.hasNext()) {
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
                        return prevAxisNeighbors.stream().map(c2 -> CoordUtils.addCoords(c1, c2));
                    }
                })
                .collect(Collectors.toList())
                ;
    }

    public static <T extends IntegerType<T>> int[] histogram(RandomAccessibleInterval<T> image, int nbins) {
        int[] bins = new int[nbins];
        Cursor<T> cursor = Views.flatIterable(image).cursor();
        while (cursor.hasNext()) {
            int val = cursor.next().getInteger();
            bins[val] = bins[val] + 1;
        }
        return bins;
    }

    public static <T extends IntegerType<T>> ContrastStretchingParams computeContrastStretchingParams(RandomAccessibleInterval<T> image,
                                                                                                      double saturationLimit,
                                                                                                      int minIntensityParam, int maxIntensityParam,
                                                                                                      int nbins) {
        int[] bins = histogram(image, nbins);
        // Convert the upper saturation limit to the number of pixels
        long totalPixels = ImageAccessUtils.getMaxSize(image.minAsLongArray(), image.maxAsLongArray());
        long upperPixelCount = (long) (totalPixels * (100.0 - saturationLimit * 0.5) / 100.0);

        int defaultMinIntensity = 0;
        int defaultMaxIntensity = 0;
        int threshold = 0;
        long count = 0;
        for (int i = 0; i < bins.length; i++) {
            int n = bins[i];
            count += bins[i];
            if (count >= upperPixelCount && threshold == 0) {
                threshold = i;
            }
            if (n > 0) {
                if (defaultMinIntensity == 0)
                    defaultMinIntensity = i;
                defaultMaxIntensity = i;
            }
        }
        int minIntensity = minIntensityParam < 0 ? defaultMinIntensity : minIntensityParam;
        int maxIntensity = maxIntensityParam < 0 ? defaultMaxIntensity : maxIntensityParam;

        return new ContrastStretchingParams(threshold, minIntensity, maxIntensity);
    }

    public static <T extends IntegerType<T>, R> R fold(RandomAccessibleInterval<T> image,
                                                       R zero,
                                                       BiFunction<R, T, R> op,
                                                       BinaryOperator<R> combiner) {
        return Views.flatIterable(image).stream().parallel().reduce(zero, op, combiner);
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
