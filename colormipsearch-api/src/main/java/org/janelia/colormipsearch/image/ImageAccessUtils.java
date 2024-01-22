package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.LocalizableSampler;
import net.imglib2.view.RandomAccessibleIntervalCursor;

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

    public static <S, T> T fold(ImageAccess<S> imageAccess, T acumulator, BiFunction<S, T, T> op) {
        Cursor<S> imgCursor = new RandomAccessibleIntervalCursor<S>(imageAccess);
        T current = acumulator;
        while (imgCursor.hasNext()) {
            current = op.apply(imgCursor.get(), current);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public static <T> Stream<LocalizableSampler<T>> stream(Cursor<T> cursor) {
        CursorLocalizableSampler<T> currentCursor = new CursorLocalizableSampler<>(cursor.copy());
        Spliterator<LocalizableSampler<T>> spliterator = new Spliterators.AbstractSpliterator<LocalizableSampler<T>>(
                Long.MAX_VALUE, 0) {

            @Override
            public boolean tryAdvance(Consumer<? super LocalizableSampler<T>> action) {
                if (!currentCursor.hasNext()) {
                    return false;
                }
                action.accept(currentCursor);
                // advance cursor
                currentCursor.fwd();
                return true;
            }
        };
        return StreamSupport.stream(spliterator, false);
    }
}
