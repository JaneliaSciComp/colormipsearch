package org.janelia.colormipsearch.image;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.BiConvertedRandomAccessibleInterval;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static <T extends Type<T>> RandomAccessibleInterval<T> createGeomTransformation(RandomAccessibleInterval<T> img, GeomTransform geomTransform) {
        return new GeomTransformRandomAccessibleInterval<>(img, geomTransform);
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> createMirrorImage(RandomAccessibleInterval<T> img, int axis) {
        return Views.invertAxis(img, axis);
    }

    public static <T extends Type<T>> RandomAccessibleInterval<T> createMIP(RandomAccessibleInterval<T> img,
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

    public static <T extends IntegerType<T>> RandomAccessibleInterval<T> enhanceContrast(RandomAccessibleInterval<T> img,
                                                                                         Supplier<T> pixelSupplier,
                                                                                         double saturationLimit,
                                                                                         int minIntensityParam,
                                                                                         int maxIntensityParam,
                                                                                         int nbins) {
        ImageAccessUtils.ContrastStretchingParams params = ImageAccessUtils.computeContrastStretchingParams(
                img, saturationLimit, minIntensityParam, maxIntensityParam, nbins);
        if (params.threshold <= params.minIntensity)
            return img;
        else
            return createPixelTransformation(
                    img,
                    (p1, p2) -> {
                        int value = p1.getInteger();
                        if (value >= params.threshold) {
                            p2.setInteger(params.maxIntensity);
                        } else {
                            double scaledValue = (double) (params.maxIntensity * (value - params.minIntensity)) / (double) (params.threshold - params.minIntensity);
                            p2.setInteger((int) scaledValue);
                        }
                    },
                    pixelSupplier);
    }

    public static <T extends RGBPixelType<T>> RandomAccessibleInterval<T> maskPixelsBelowThreshold(RandomAccessibleInterval<T> img, int threshold) {
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
        BiPredicate<long[], T> maskPixelIsNotSet = (long[] pos, T pixel) -> {
            if (mask == null) {
                return false;
            }
            int maskPixelValue = mask.getAt(pos).getInteger();
            return maskPixelValue == 0; // if mask pixel is black then the pixel at pos should be masked
        };
        return ImageTransforms.maskPixelsMatchingCond(img, maskPixelIsNotSet, foreground);
    }

    public static <T extends IntegerType<T>> RandomAccessibleInterval<T> maskPixelsMatchingCond(RandomAccessibleInterval<T> img,
                                                                                                BiPredicate<long[], T> maskCond,
                                                                                                T foreground) {
        Supplier<T> foregroundPixelSupplier = () -> foreground;
        return new MaskedPixelRandomAccessibleInterval<>(
                img,
                maskCond,
                foregroundPixelSupplier
        );
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

    public static <T extends RGBPixelType<T>> RandomAccessibleInterval<UnsignedByteType> createRGBToSignalTransformation(RandomAccessibleInterval<T> img,
                                                                                                                         int signalThreshold) {
        Converter<T, UnsignedByteType> rgbToIntensity = new AbstractRGBToIntensityConverter<T, UnsignedByteType>(false) {
            @Override
            public void convert(T rgb, UnsignedByteType signal) {
                int intensity = getIntensity(rgb, 255);
                signal.set(intensity > signalThreshold ? 1 : 0);
            }
        };
        return ImageTransforms.createPixelTransformation(img, rgbToIntensity, () -> new UnsignedByteType(0));
    }

    public static <R extends Type<R>, S extends Type<S>, T extends Type<T>>
    RandomAccessibleInterval<T> createBinaryOperation(RandomAccessibleInterval<R> img1,
                                                      RandomAccessibleInterval<S> img2,
                                                      BiConverter<R, S, T> op,
                                                      T resultBackground
    ) {
        Supplier<BiConverter<? super R, ? super S, ? super T>> pixelConverterSupplier = () -> op;
        Supplier<T> backgroundSupplier = () -> resultBackground;
        return new BiConvertedRandomAccessibleInterval<R, S, T>(
                img1,
                img2,
                pixelConverterSupplier,
                backgroundSupplier
        );
    }

    public static <P extends Type<P>, Q extends Type<Q>, R extends Type<R>, S extends Type<S>, T extends Type<T>>
    RandomAccessibleInterval<T> createQuadPixelTransformation(RandomAccessibleInterval<P> img1,
                                                              RandomAccessibleInterval<Q> img2,
                                                              RandomAccessibleInterval<R> img3,
                                                              RandomAccessibleInterval<S> img4,
                                                              QuadConverter<P, Q, R, S, T> op,
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

    public static <T extends RealType<T>> RandomAccessibleInterval<T> scaleImage(RandomAccessibleInterval<T> img, double[] scaleFactors) {
        RandomAccessibleInterval<T> scaledImage = img;
        for (int d = scaleFactors.length - 1; d >= 0; d--) {
            scaledImage = new ScaleTransformRandomAccessibleInterval<>(
                    scaledImage,
                    scaleFactors[d],
                    d
            );
        }
        return scaledImage;
    }

}
