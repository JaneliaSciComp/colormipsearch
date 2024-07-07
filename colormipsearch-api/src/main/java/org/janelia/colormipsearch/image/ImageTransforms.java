package org.janelia.colormipsearch.image;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.imglib2.Interval;
import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.BiConvertedRandomAccessibleInterval;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static <T extends Type<T>> ImageAccess<T> createGeomTransformation(ImageAccess<T> img, GeomTransform geomTransform) {
        return new SimpleImageAccess<>(
                img,
                imgAccess -> new GeomTransformRandomAccess<>(imgAccess, geomTransform),
                img.getBackgroundValue()
        );
    }

    public static <T extends Type<T>> ImageAccess<T> createMirrorImage(ImageAccess<T> img, int axis) {
        return createGeomTransformation(img, new MirrorTransform(img.getImageShape(), axis));
    }

    public static <T extends Type<T>> ImageAccess<T> createMIP(ImageAccess<T> img,
                                                               Comparator<T> pixelComparator, int axis, long minAxis, long maxAxis) {
        return new SimpleImageAccess<T>(
                new MIPProjectionRandomAccessibleInterval<>(
                        img,
                        pixelComparator,
                        axis,
                        Math.max(img.min(axis), minAxis),
                        Math.min(img.max(axis), maxAxis)
                ),
                img.getBackgroundValue()
        );
    }

    public static <T extends IntegerType<T>> ImageAccess<T> enhanceContrast(ImageAccess<T> img,
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
                            double scaledValue = (double)(params.maxIntensity * (value - params.minIntensity)) / (double)(params.threshold - params.minIntensity);
                            p2.setInteger((int)scaledValue);
                        }
                    },
                    img.getBackgroundValue());
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> maskPixelsBelowThreshold(ImageAccess<T> img,
                                                                                      int threshold) {
        BiPredicate<long[], T> isRGBBelowThreshold = (long[] pos, T pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= threshold && g <= threshold && b <= threshold;
        };
        return ImageTransforms.maskPixelsMatchingCond(img, isRGBBelowThreshold);
    }

    public static <T extends Type<T>, M extends Type<M>> ImageAccess<T> maskPixelsUsingMaskImage(ImageAccess<T> img,
                                                                                                 ImageAccess<M> mask) {
        BiPredicate<long[], T> maskPixelIsNotSet = (long[] pos, T pixel) -> {
            if (mask == null) {
                return false;
            }
            M maskPixel = mask.getAt(pos);
            return mask.isBackgroundValue(maskPixel); // if mask pixel is black then the pixel at pos should be masked
        };
        return ImageTransforms.maskPixelsMatchingCond(img, maskPixelIsNotSet);
    }

    public static <T extends Type<T>> ImageAccess<T> maskPixelsMatchingCond(ImageAccess<T> img, BiPredicate<long[], T> maskCond) {
        T imgBackground = img.getBackgroundValue();
        return new SimpleImageAccess<>(
                img,
                imgAccess -> new MaskedPixelAccess<>(imgAccess, maskCond, imgBackground),
                imgBackground
        );
    }

    public static <S extends Type<S>, T extends Type<T>>
    ImageAccess<T> createPixelTransformation(ImageAccess<S> img,
                                             Converter<S, T> pixelConverter,
                                             T resultBackground) {
        Supplier<Converter<? super S, ? super T>> pixelConverterSupplier = () -> pixelConverter;
        Supplier<T> backgroundSupplier = () -> resultBackground;
        return new SimpleImageAccess<T>(
                new ConvertedRandomAccessibleInterval<S, T>(
                        img,
                        pixelConverterSupplier,
                        backgroundSupplier
                ),
                resultBackground
        );
    }

    public static <T extends RGBPixelType<T>> ImageAccess<UnsignedByteType> createRGBToSignalTransformation(ImageAccess<T> img,
                                                                                                            int signalThreshold) {
        Converter<T, UnsignedByteType> rgbToIntensity = new AbstractRGBToIntensityConverter<T, UnsignedByteType>(false) {
            @Override
            public void convert(T rgb, UnsignedByteType signal) {
                int intensity = getIntensity(rgb, 255);
                signal.set(intensity > signalThreshold ? 1 : 0);
            }
        };
        return ImageTransforms.createPixelTransformation(img, rgbToIntensity, new UnsignedByteType(0));
    }

    public static <R extends Type<R>, S extends Type<S>, T extends Type<T>>
    ImageAccess<T> createBinaryPixelTransformation(ImageAccess<R> img1,
                                                   ImageAccess<S> img2,
                                                   BiConverter<R, S, T> op,
                                                   T resultBackground
    ) {
        Supplier<BiConverter<? super R, ? super S, ? super T>> pixelConverterSupplier = () -> op;
        Supplier<T> backgroundSupplier = () -> resultBackground;
        return new SimpleImageAccess<T>(
                new BiConvertedRandomAccessibleInterval<R, S, T>(
                        img1,
                        img2,
                        pixelConverterSupplier,
                        backgroundSupplier
                ),
                resultBackground
        );
    }

    public static <P extends Type<P>, Q extends Type<Q>, R extends Type<R>, S extends Type<S>, T extends Type<T>>
    ImageAccess<T> createQuadPixelTransformation(ImageAccess<P> img1, ImageAccess<Q> img2, ImageAccess<R> img3, ImageAccess<S> img4,
                                                 QuadConverter<P, Q, R, S, T> op,
                                                 T resultBackground
    ) {
        Supplier<QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T>> pixelConverterSupplier = () -> op;
        Supplier<T> backgroundSupplier = () -> resultBackground;
        return new SimpleImageAccess<T>(
                new QuadConvertedRandomAccessibleInterval<P, Q, R, S, T>(
                        img1,
                        img2,
                        img3,
                        img4,
                        pixelConverterSupplier,
                        backgroundSupplier
                ),
                resultBackground
        );
    }

    public static <T extends Type<T>> ImageAccess<T> createHyperSphereDilationTransformation(
            ImageAccess<T> img,
            Supplier<PixelHistogram<T>> neighborhoodHistogramSupplier,
            long[] radii
    ) {
        T backgroundPixel = img.getBackgroundValue().copy();
        return new SimpleImageAccess<T>(
                new MaxFilterRandomAccessibleInterval<>(
                        img,
                        radii,
                        neighborhoodHistogramSupplier.get()
                ),
                backgroundPixel
        );
    }

}
