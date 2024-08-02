package org.janelia.colormipsearch.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;

public class TestUtils {

    private static final boolean DISPLAY_TEST_IMAGES = Boolean.getBoolean("display.testImages");

    /**
     * This is a test method that we only use when debugging
     * if we want to inspect the result images before the test terminates.
     */
    public static void waitForKey() {
        try {
            System.in.read();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class RGBComparator<T extends RGBPixelType<T>> implements Comparator<T> {

        @Override
        public int compare(T v1, T v2) {
            if (v1.getRed() < v2.getRed()) {
                return -1;
            } else if (v1.getRed() > v2.getRed()) {
                return 1;
            } else if (v1.getGreen() < v2.getGreen()) {
                return -1;
            } else if (v1.getGreen() < v2.getGreen()) {
                return 1;
            } else if (v1.getBlue() < v2.getBlue()) {
                return -1;
            } else if (v1.getBlue() > v2.getBlue()) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public static ImagePlus img2ImagePlus(Img<UnsignedShortType> img, String title) {
        ImageStack imageStack = new ImageStack((int)img.dimension(0), (int)img.dimension(1));
        int maxDepth = (int) img.dimension(2);
        for (int z = 0; z < maxDepth; z++) {
            IntervalView<UnsignedShortType> slice = Views.hyperSlice(img, 2, z);
            ImagePlus sliceImagePlus = ImageJFunctions.wrapFloat(
                    slice,
                    (UnsignedShortType s, FloatType t) -> t.setReal(s.getInteger()),
                    "Slice " + z);
            imageStack.addSlice(sliceImagePlus.getProcessor());
        }
        return new ImagePlus(title, imageStack);
    }

    public static <S extends IntegerType<S>, T extends NumericType<T>> void displayImage(
            RandomAccessibleInterval<S> image,
            Converter<S, T> displayConverter,
            T background) {
        if (DISPLAY_TEST_IMAGES) {
            RandomAccessibleInterval<T> displayableImage = ImageTransforms.createPixelTransformation(
                    image,
                    displayConverter,
                    () -> background
            );
            ImageJFunctions.show(displayableImage);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends RGBPixelType<T>> void displayRGBImage(RandomAccessibleInterval<? extends RGBPixelType<?>> rgbImage) {
        displayImage(
                (RandomAccessibleInterval<T>) rgbImage,
                (T rgb, ARGBType p) -> p.set(rgb.getInteger()),
                new ARGBType(0)
        );
    }

    public static <T extends NumericType<T>> void displayNumericImage(RandomAccessibleInterval<?> img) {
        if (DISPLAY_TEST_IMAGES) {
            ImageJFunctions.show((RandomAccessibleInterval<T>) img);
        }
    }

    public static <T extends NumericType<T>> void displayIJImage(ImagePlus img) {
        if (DISPLAY_TEST_IMAGES) {
            img.show();
        }
    }

    public static <T> int compareImages(RandomAccessibleInterval<T> refImage, RandomAccessibleInterval<T> testImage, Comparator<T> comparator) {
        assertArrayEquals(refImage.dimensionsAsLongArray(), testImage.dimensionsAsLongArray());
        assertNotSame(refImage, testImage);
        Cursor<T> refImageCursor = new RandomAccessibleIntervalCursor<>(refImage);
        Cursor<T> testImageCursor = new RandomAccessibleIntervalCursor<>(testImage);
        int res = 0;
        while (refImageCursor.hasNext()) {
            refImageCursor.fwd();
            testImageCursor.fwd();
            T refPixel = refImageCursor.get();
            T testPixel = testImageCursor.get();
            int pixCompRes = comparator.compare(refPixel, testPixel);
            if (pixCompRes != 0 && res == 0) {
                res = pixCompRes;
            }
        }
        return res;
    }

    public static <T extends RealType<T>> long countDiffs(RandomAccessibleInterval<T> refImage, RandomAccessibleInterval<T> testImage) {
        assertArrayEquals(refImage.dimensionsAsLongArray(), testImage.dimensionsAsLongArray());
        assertNotSame(refImage, testImage);
        Cursor<T> refImageCursor = new RandomAccessibleIntervalCursor<>(refImage);
        Cursor<T> testImageCursor = new RandomAccessibleIntervalCursor<>(testImage);
        Comparator<T> pxValueComparator = Comparator.comparing(ComplexType::getRealDouble);
        long res = 0;
        while (refImageCursor.hasNext()) {
            refImageCursor.fwd();
            testImageCursor.fwd();
            T refPixel = refImageCursor.get();
            T testPixel = testImageCursor.get();
            if (pxValueComparator.compare(refPixel, testPixel) != 0) {
                res++;
            }
        }
        return res;
    }

}
