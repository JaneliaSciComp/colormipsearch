package org.janelia.colormipsearch.image;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.BiConvertedRandomAccessibleInterval;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.loops.IntervalChunks;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.loops.LoopUtils;
import net.imglib2.parallel.Parallelization;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static <T extends RGBPixelType<T>> Converter<T, UnsignedByteType> getRGBToSignalConverter(int threshold) {
        return new AbstractRGBToIntensityConverter<T, UnsignedByteType>(false) {
            @Override
            public void convert(T rgb, UnsignedByteType signal) {
                int intensity = getIntensity(rgb, 255);
                signal.set(intensity > threshold ? 1 : 0);
            }
        };
    }

    public static <S extends RGBPixelType<S>, T extends RealType<T>> Converter<S, T> getRGBToIntensity(boolean withGammaCorrection) {
        return new AbstractRGBToIntensityConverter<S, T>(withGammaCorrection) {
            @Override
            public void convert(S rgb, T t) {
                int intensity = getIntensity(rgb, 255);
                t.setReal(intensity);
            }
        };
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> createGeomTransformation(RandomAccessibleInterval<T> img, GeomTransform geomTransform) {
        return new GeomTransformRandomAccessibleInterval<>(img, geomTransform);
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> mirrorImage(RandomAccessibleInterval<T> img, int axis) {
        return new GeomTransformRandomAccessibleInterval<>(img, new MirrorTransform(img.dimensionsAsLongArray(), axis));
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> maxIntensityProjection(RandomAccessibleInterval<T> img,
                                                                                         Comparator<T> pixelComparator,
                                                                                         int axis, long minAxis, long maxAxis) {
        return new MIPProjectionRandomAccessibleInterval<>(
                img,
                pixelComparator,
                axis,
                Math.max(img.min(axis), minAxis),
                Math.min(img.max(axis), maxAxis)
        );
    }

    public static <T extends RGBPixelType<T>> RandomAccessibleInterval<T> maskRGBPixelsBelowThreshold(RandomAccessibleInterval<T> img, int threshold) {
        BiPredicate<long[], T> isRGBBelowThreshold = (long[] pos, T pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= threshold && g <= threshold && b <= threshold;
        };
        return ImageTransforms.maskPixelsMatchingCond(img, isRGBBelowThreshold, null);
    }

    public static <T extends IntegerType<T>, M extends IntegerType<M>> RandomAccessibleInterval<T> maskPixelsUsingMaskImage(RandomAccessibleInterval<T> img,
                                                                                                                            RandomAccessibleInterval<M> mask,
                                                                                                                            T foreground) {
        if (mask == null) {
            return img;
        } else {
            return createBinaryPixelOperation(
                    img,
                    mask,
                    (p, m, r) -> {
                        if (m.getInteger() == 0) {
                            r.setZero();
                        } else {
                            r.set(p);
                        }
                    },
                    foreground
            );
        }
    }

    public static <T extends IntegerType<T>> RandomAccessibleInterval<T> maskPixelsMatchingCond(RandomAccessibleInterval<T> img,
                                                                                                @Nullable BiPredicate<long[], ? super T> maskCond,
                                                                                                T foreground) {
        if (maskCond == null) {
            return img;
        } else {
            Supplier<T> foregroundPixelSupplier = () -> foreground;
            return new MaskedPixelRandomAccessibleInterval<>(
                    img,
                    maskCond,
                    foregroundPixelSupplier
            );
        }
    }

    public static <S extends Type<S>, T extends Type<T>>
    RandomAccessibleInterval<T> createPixelTransformation(RandomAccessibleInterval<S> img,
                                                          Converter<S, T> pixelConverter,
                                                          Supplier<T> targetPixelSupplier) {
        Supplier<Converter<? super S, ? super T>> pixelConverterSupplier = () -> pixelConverter;
        return new ConvertedRandomAccessibleInterval<S, T>(
                img,
                pixelConverterSupplier,
                targetPixelSupplier
        );
    }

    public static <S extends IntegerType<S>, T extends IntegerType<T>>
    RandomAccessibleInterval<T> binarizeImage(RandomAccessibleInterval<S> img, int lowerThreshold, int upperThreshold, T targetPxType) {
        final T maxValPx = targetPxType.createVariable();
        double maxRealValue = maxValPx.getMaxValue();
        maxValPx.setReal(maxRealValue);
        int maxIntValue = maxValPx.getInteger();
        Supplier<T> targetPixelSupplier = () -> targetPxType;
        return new ConvertedRandomAccessibleInterval<S, T>(
                img,
                (s, t) -> {
                    int sVal = s.getInteger();
                    t.setInteger(sVal >= lowerThreshold && sVal <= upperThreshold ? maxIntValue : 0);
                },
                targetPixelSupplier
        );
    }

    public static <S extends RGBPixelType<S>, T extends IntegerType<T>> RandomAccessibleInterval<T> rgbToIntensityTransformation(
            RandomAccessibleInterval<S> img, T targetPxType, boolean withGammaCorrection) {
        Converter<S, T> rgbToIntensity = getRGBToIntensity(withGammaCorrection);
        return ImageTransforms.createPixelTransformation(img, rgbToIntensity, () -> targetPxType);
    }

    @SuppressWarnings("unchecked")
    public static <T extends RGBPixelType<T>> RandomAccessibleInterval<UnsignedByteType> rgbToSignalTransformation(RandomAccessibleInterval<? extends RGBPixelType<?>> img,
                                                                                                                   int signalThreshold) {
        Converter<T, UnsignedByteType> rgbToSignal = getRGBToSignalConverter(signalThreshold);
        return ImageTransforms.createPixelTransformation(
                (RandomAccessibleInterval<T>) img,
                rgbToSignal,
                () -> new UnsignedByteType(0));
    }

    public static <R extends IntegerType<R>, S extends IntegerType<S>, T extends IntegerType<T>>
    RandomAccessibleInterval<T> andOp(RandomAccessibleInterval<R> img1, RandomAccessibleInterval<S> img2, T resultPxType) {
        return ImageTransforms.createBinaryPixelOperation(
                img1,
                img2,
                (p1, p2, r) -> {
                    r.setInteger(p1.getInteger() & p2.getInteger());
                },
                resultPxType
        );
    }

    public static <R extends Type<R>, S extends Type<S>, T extends Type<T>>
    RandomAccessibleInterval<T> createBinaryPixelOperation(RandomAccessibleInterval<R> img1,
                                                           RandomAccessibleInterval<S> img2,
                                                           BiConverter<? super R, ? super S, ? super T> op,
                                                           T resultPxType
    ) {
        Supplier<BiConverter<? super R, ? super S, ? super T>> pixelConverterSupplier = () -> op;
        Supplier<T> resultPxTypeSupplier = () -> resultPxType;

        return new BiConvertedRandomAccessibleInterval<R, S, T>(
                img1,
                img2,
                pixelConverterSupplier,
                resultPxTypeSupplier
        );
    }

    public static <P extends Type<P>, Q extends Type<Q>, R extends Type<R>, S extends Type<S>, T extends Type<T>>
    RandomAccessibleInterval<T> createQuadPixelOperation(RandomAccessibleInterval<P> img1,
                                                         RandomAccessibleInterval<Q> img2,
                                                         RandomAccessibleInterval<R> img3,
                                                         RandomAccessibleInterval<S> img4,
                                                         QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T> op,
                                                         T resultBackground) {
        Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> pixelConverterSupplier = () -> op;
        Supplier<T> backgroundSupplier = () -> resultBackground;
        return new QuadConvertedRandomAccessibleInterval<P, Q, R, S, T>(
                img1,
                img2,
                img3,
                img4,
                pixelConverterSupplier,
                backgroundSupplier
        );
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> dilateImage(
            RandomAccessibleInterval<T> img,
            Supplier<PixelHistogram<T>> neighborhoodHistogramSupplier,
            int[] radii
    ) {
        return new MaxFilterRandomAccessibleInterval<>(
                img,
                radii,
                neighborhoodHistogramSupplier
        );
    }

    public static <T extends Type<T>> void parallelDilateImage(RandomAccessibleInterval<T> sourceImg,
                                                               RandomAccessibleInterval<T> targetImg,
                                                               Supplier<PixelHistogram<T>> neighborhoodHistogramSupplier,
                                                               int[] radii,
                                                               ExecutorService executorService) {

        RandomAccessibleInterval<T> dilatedView = new MaxFilterRandomAccessibleInterval<>(
                sourceImg,
                radii,
                neighborhoodHistogramSupplier
        );
        int ndims = sourceImg.numDimensions();
        TaskExecutor taskExecutor = TaskExecutors.forExecutorServiceAndNumTasks(
                executorService,
                (int)sourceImg.dimension(ndims-1));
        LoopBuilder.setImages(dilatedView, targetImg).multiThreaded(taskExecutor).forEachPixel((s, t) -> t.set(s));
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> dilateImageInterval(
            RandomAccessibleInterval<T> img,
            Supplier<PixelHistogram<T>> neighborhoodHistogramSupplier,
            int[] radii,
            Interval interval
    ) {
        return new MaxFilterRandomAccessibleInterval<>(
                interval != null ? Views.interval(img, interval) : img,
                radii,
                neighborhoodHistogramSupplier
        );
    }

    public static <T extends RGBPixelType<T>> RandomAccessibleInterval<T> dilateRGBImage1d(
            RandomAccessibleInterval<T> img,
            int radius,
            int axis
    ) {
        return new MaxFilter1dRandomAccessibleInterval<>(
                img,
                (p1, p2, r) -> {
                    int r1 = p1.getRed();
                    int g1 = p1.getGreen();
                    int b1 = p1.getBlue();
                    int r2 = p2.getRed();
                    int g2 = p2.getGreen();
                    int b2 = p2.getBlue();
                    r.setFromRGB(
                            Math.max(r1, r2),
                            Math.max(g1, g2),
                            Math.max(b1, b2)
                    );
                },
                radius,
                axis
        );
    }

    public static <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> scaleImage(RandomAccessibleInterval<T> img,
                                                                                                    long[] scaledDimensions,
                                                                                                    T pxType) {
        RandomAccessibleInterval<T> scaledImage = img;
        for (int d = scaledDimensions.length - 1; d >= 0; d--) {
            if (pxType != null) {
                scaledImage = ImageAccessUtils.materializeAsNativeImg(
                        new ScaleTransformRandomAccessibleInterval<>(
                                scaledImage,
                                scaledDimensions[d],
                                d
                        ),
                        null,
                        pxType
                );
            } else {
                scaledImage = new ScaleTransformRandomAccessibleInterval<>(
                        scaledImage,
                        scaledDimensions[d],
                        d
                );
            }
        }
        return scaledImage;
    }

}
