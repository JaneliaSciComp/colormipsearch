package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.BiConvertedRandomAccessibleInterval;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static <T> ImageAccess<T> createGeomTransformation(ImageAccess<T> img, GeomTransform geomTransform) {
        return new SimpleImageAccess<>(
                img,
                imgAccess -> new GeomTransformRandomAccess<>(imgAccess, geomTransform),
                img.getBackgroundValue()
        );
    }

    public static <T> ImageAccess<T> createMirrorTransformation(ImageAccess<T> img, int axis) {
        return createGeomTransformation(img, new MirrorTransform(img.getImageShape(), axis));
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

    public static <T, M> ImageAccess<T> maskPixelsUsingMaskImage(ImageAccess<T> img, ImageAccess<M> mask) {
        BiPredicate<long[], T> maskPixelIsNotSet = (long[] pos, T pixel) -> {
            if (mask == null) {
                return false;
            }
            M maskPixel = mask.getAt(pos);
            return mask.isBackgroundValue(maskPixel); // if mask pixel is black then the pixel at pos should be masked
        };
        return ImageTransforms.maskPixelsMatchingCond(img, maskPixelIsNotSet);
    }

    public static <T> ImageAccess<T> maskPixelsMatchingCond(ImageAccess<T> img, BiPredicate<long[], T> maskCond) {
        T imgBackground = img.getBackgroundValue();
        return new SimpleImageAccess<>(
                img,
                imgAccess -> new MaskedPixelAccess<>(imgAccess, maskCond, imgBackground),
                imgBackground
        );
    }

    public static <S, T> ImageAccess<T> createPixelTransformation(ImageAccess<S> img,
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

    public static <R, S, T> ImageAccess<T> createBinaryPixelTransformation(
            ImageAccess<R> img1,
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

    public static <P, Q, R, S, T> ImageAccess<T> createQuadPixelTransformation(
            ImageAccess<P> img1,
            ImageAccess<Q> img2,
            ImageAccess<R> img3,
            ImageAccess<S> img4,
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

    public static <T extends RGBPixelType<T>> ImageAccess<T> createHyperSphereDilationTransformation(
            ImageAccess<T> img,
            int radius
    ) {
        T backgroundPixel = img.getBackgroundValue().copy();
        PixelHistogram<T> neighborhoodHistogram = new RGBPixelHistogram<>(backgroundPixel);
        return new SimpleImageAccess<T>(
                new MaxFilterRandomAccessibleInterval<>(
                        img,
                        radius,
                        neighborhoodHistogram
                ),
                backgroundPixel
        );
    }
}
