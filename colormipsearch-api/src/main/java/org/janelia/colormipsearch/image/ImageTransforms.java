package org.janelia.colormipsearch.image;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class ImageTransforms {

    public static final UnsignedByteType SIGNAL = new UnsignedByteType(1);
    public static final UnsignedByteType NO_SIGNAL = new UnsignedByteType(0);

    /**
     * This is a 4 parameter function.
     *
     * @param <P>
     * @param <Q>
     * @param <R>
     * @param <S>
     * @param <T>
     */
    @FunctionalInterface
    public interface QuadTupleFunction<P, Q, R, S, T> extends Serializable {
        T apply(P p, Q q, R r, S s);

        default <U> QuadTupleFunction<P, Q, R, S, U> andThen(Function<? super T, ? extends U> after) {
            return (P p, Q q, R r, S s) -> after.apply(apply(p, q, r, s));
        }
    }

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

    public static <S, T> ImageAccess<T> createPixelTransformation(ImageAccess<S> img, PixelConverter<S, T> pixelConverter) {
        return new SimpleImageAccess<>(
                new ConvertPixelAccess<>(
                        img.randomAccess(),
                        pixelConverter
                ),
                img,
                pixelConverter.convert(img.getBackgroundValue())
        );
    }

    public static <T extends RGBPixelType<T>> ImageAccess<UnsignedByteType> createRGBToSignalTransformation(ImageAccess<T> img,
                                                                                                            int signalThreshold) {
        PixelConverter<T, UnsignedByteType> rgbToSignal = new RGBToIntensityPixelConverter<T>(false)
                .andThen(p -> p.get() > signalThreshold ? SIGNAL : NO_SIGNAL);
        return ImageTransforms.createPixelTransformation((ImageAccess<T>) img, rgbToSignal);
    }

    public static <R, S, T> ImageAccess<T> createBinaryOpTransformation(
            ImageAccess<R> img1,
            ImageAccess<S> img2,
            BiFunction<R, S, T> op
    ) {
        return new SimpleImageAccess<>(
                new BinaryPixelOpAccess<>(img1.randomAccess(), img2.randomAccess(), img1, op),
                img1,
                op.apply(img1.getBackgroundValue(), img2.getBackgroundValue())
        );
    }

    public static <P, Q, R, S, T> ImageAccess<T> createQuadOpTransformation(
            ImageAccess<P> img1,
            ImageAccess<Q> img2,
            ImageAccess<R> img3,
            ImageAccess<S> img4,
            QuadTupleFunction<P, Q, R, S, T> op
    ) {
        return new SimpleImageAccess<>(
                new QuadPixelOpAccess<>(
                        img1.randomAccess(),
                        img2.randomAccess(),
                        img3.randomAccess(),
                        img4.randomAccess(),
                        img1,
                        op),
                img1,
                op.apply(
                    img1.getBackgroundValue(),
                    img2.getBackgroundValue(),
                    img3.getBackgroundValue(),
                    img4.getBackgroundValue())
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
        List<HistogramWithPixelLocations<T>> pixelHistograms = accessibleNeighborhoods.stream()
                .map(na -> new RGBPixelHistogram<T>(img.numDimensions()))
                .collect(Collectors.toList());
        T dilatedPixel = img.getBackgroundValue();
        return new SimpleImageAccess<>(
                new MaxFilterRandomAccess<>(
                        img.randomAccess(),
                        img,
                        accessibleNeighborhoods,
                        pixelHistograms,
                        (T rgb1, T rgb2) -> {
                            int r1 = rgb1.getRed();
                            int g1 = rgb1.getGreen();
                            int b1 = rgb1.getBlue();

                            int r2 = rgb2.getRed();
                            int g2 = rgb2.getGreen();
                            int b2 = rgb2.getBlue();
                            dilatedPixel.setFromRGB(
                                    Math.max(r1, r2),
                                    Math.max(g1, g2),
                                    Math.max(b1, b2)
                            );
                            return dilatedPixel;
                        }
                ),
                img,
                img.getBackgroundValue()
        );
    }
}
