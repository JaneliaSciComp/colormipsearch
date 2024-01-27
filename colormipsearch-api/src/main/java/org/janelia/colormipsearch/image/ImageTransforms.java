package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
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

    @SuppressWarnings("unchecked")
    public static <T extends RGBPixelType<?>> ImageAccess<T> createThresholdedMaskTransformation(ImageAccess<T> img,
                                                                                                 int threshold) {
        BiPredicate<long[], T> isRGBBelowThreshold = (long[] pos, T pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= threshold && g <= threshold && b <= threshold;
        };
        return ImageTransforms.createMaskTransformation(img, isRGBBelowThreshold);
    }

    public static <T> ImageAccess<T> createMaskTransformation(ImageAccess<T> img, BiPredicate<long[], T> maskCond) {
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
                pixelConverter.convertTo(img.getBackgroundValue())
        );
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> createHyperSphereDilationTransformation(
            ImageAccess<T> img,
            int radius
    ) {
        List<Shape> strElements = StructuringElements.disk(radius, img.numDimensions());
//                Arrays.asList(new HyperSphereShape(radius));
        RandomAccessibleInterval<T> extendedImg = Views.interval(
                Views.extendBorder(img),
                Intervals.expand(img, radius)
        );
//        WrappedRandomAccess<T> extendedImgAccess = new WrappedRandomAccess<>(extendedInterval.randomAccess());
//        RandomAccessibleInterval<T> extendedImg = new SimpleImageAccess<>(
//                extendedImgAccess,
//                extendedInterval,
//                img.getBackgroundValue()
//        );
        List<RandomAccess<Neighborhood<T>>> accessibleNeighborhoods = strElements.stream()
                .map(strel -> strel.neighborhoodsRandomAccessible(extendedImg))
                .map(neighborhoodRandomAccessible -> neighborhoodRandomAccessible.randomAccess(img))
                .collect(Collectors.toList());
        Interval boundingBox = strElements.stream()
                .map(s -> s.getStructuringElementBoundingBox(img.numDimensions()))
                .reduce((i1, i2) -> Intervals.union(i1, i2))
                .orElse(null);
        return new SimpleImageAccess<>(
                new MaxFilterRandomAccess<>(
                        img.randomAccess(),
                        img,
                        accessibleNeighborhoods,
                        (T rgb1, T rgb2) -> {
                            int r1 = rgb1.getRed();
                            int g1 = rgb1.getGreen();
                            int b1 = rgb1.getBlue();

                            int r2 = rgb2.getRed();
                            int g2 = rgb2.getGreen();
                            int b2 = rgb2.getBlue();
                            ARGBType rgb = new ARGBType(ARGBType.rgba(
                                    Math.max(r1, r2),
                                    Math.max(g1, g2),
                                    Math.max(b1, b2),
                                    255));
                            return img.getBackgroundValue().fromARGBType(rgb);
                        },
                        (long[] pos, T newVal) -> {
//                            extendedImgAccess.updateValue(pos, newVal);
                        }
                ),
                img,
                img.getBackgroundValue()
        );
    }
}
