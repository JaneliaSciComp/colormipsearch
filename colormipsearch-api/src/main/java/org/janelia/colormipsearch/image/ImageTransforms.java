package org.janelia.colormipsearch.image;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.numeric.ARGBType;
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

    public static <T extends RGBPixelType<T>> ImageAccess<T> createHyperSphereDilationTransformation(ImageAccess<T> img, int radius) {
        HyperSphereShape neighborhoodShape = new HyperSphereShape(radius);
        RandomAccessibleInterval<T> extendedImg = Views.interval( Views.extendBorder( img ), img );

        Iterable<Neighborhood<T>> neighborhoods = neighborhoodShape.neighborhoods(extendedImg);
        return new SimpleImageAccess<>(
                new MaxFilterRandomAccess<>(
                        extendedImg.randomAccess(),
                        extendedImg,
                        radius,
                        (rgb1, rgb2) -> {
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
                        }
                ),
                Views.interval(extendedImg,
                        new long[]{radius, radius},
                        ImageAccessUtils.addCoords(extendedImg.maxAsLongArray(), new long[]{-radius, -radius})),
                img.getBackgroundValue()
        );
    }
}
