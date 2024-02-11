package org.janelia.colormipsearch.image;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.BiConvertedRandomAccess;
import net.imglib2.converter.read.ConvertedRandomAccess;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static <T> ImageAccess<T> createGeomTransformation(ImageAccess<T> img, GeomTransform geomTransform) {
        return new SimpleImageAccess<>(
                new GeomTransformRandomAccess<>(img.randomAccess(), img, geomTransform),
                img,
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
        return new SimpleImageAccess<>(
                new MaskedPixelAccess<>(
                        img.randomAccess(),
                        maskCond,
                        img.getBackgroundValue()
                ),
                img,
                img.getBackgroundValue()
        );
    }

    public static <S, T> ImageAccess<T> createPixelTransformation(ImageAccess<S> img,
                                                                  Converter<S, T> pixelConverter,
                                                                  T resultBackground) {

        return new SimpleImageAccess<>(
                new ConvertedRandomAccess<S, T>(
                        img.randomAccess(),
                        () -> pixelConverter,
                        () -> resultBackground),
                img,
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
        return new SimpleImageAccess<>(
                new BiConvertedRandomAccess<>(
                        img1.randomAccess(),
                        img2.randomAccess(),
                        () -> op,
                        () -> resultBackground
                ),
                img1,
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
        return new SimpleImageAccess<>(
                new QuadConvertedRandomAccess<>(
                        img1.randomAccess(),
                        img2.randomAccess(),
                        img3.randomAccess(),
                        img4.randomAccess(),
                        () -> op,
                        () -> resultBackground),
                img1,
                resultBackground
        );
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> createHyperSphereDilationTransformation(
            ImageAccess<T> img,
            int radius
    ) {
        // HyperSphereShape(radius) is very slow but
        // StructuringElements.disk(radius, img.numDimensions()) doesn't seem to be correct
        List<Shape> strElements = Collections.singletonList(new HyperSphereShape(radius));
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(img),
                Intervals.expand(img, radius)
        );
        List<RandomAccess<Neighborhood<T>>> accessibleNeighborhoods = strElements.stream()
                .map(strel -> strel.neighborhoodsRandomAccessible(extendedImg))
                .map(neighborhoodRandomAccessible -> neighborhoodRandomAccessible.randomAccess(img))
                .collect(Collectors.toList());
        T dilatedPixel = img.getBackgroundValue().copy();
        return new SimpleImageAccess<>(
                new MaxFilterRandomAccess<>(
                        img.randomAccess(),
                        new RGBPixelHistogram<>(dilatedPixel, img.numDimensions()),
                        accessibleNeighborhoods,
                        (T rgb) -> {
                            int r = rgb.getRed();
                            int g = rgb.getGreen();
                            int b = rgb.getBlue();
                            dilatedPixel.setFromRGB(r, g, b);
                            return dilatedPixel;

                        },
                        (T rgb) -> {
                            /*
                             * The method returns the channel wise max value between the current value and the input param, i.e.
                             * RGB(max(r1,r2), max(g1,g2), max(b1,b2))
                             */
                            int r1 = dilatedPixel.getRed();
                            int g1 = dilatedPixel.getGreen();
                            int b1 = dilatedPixel.getBlue();

                            int r2 = rgb.getRed();
                            int g2 = rgb.getGreen();
                            int b2 = rgb.getBlue();
                            dilatedPixel.setFromRGB(
                                    r1 > r2 ? r1 : r2,
                                    g1 > g2 ? g1 : g2,
                                    b1 > b2 ? b1 : b2
                            );
                            return dilatedPixel;
                        }
                ),
                img,
                img.getBackgroundValue()
        );
    }
}
