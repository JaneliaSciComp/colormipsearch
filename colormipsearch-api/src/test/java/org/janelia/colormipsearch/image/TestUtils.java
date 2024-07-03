package org.janelia.colormipsearch.image;

import java.util.Comparator;

import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import org.janelia.colormipsearch.image.type.RGBPixelType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class TestUtils {

    private static final boolean DISPLAY_TEST_IMAGES = true; // Boolean.getBoolean("display.testImages");

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

    public static <T extends RGBPixelType<T>> void displayRGBImage(ImageAccess<T> rgbImage) {
        if (DISPLAY_TEST_IMAGES) {
            ImageAccess<ARGBType> displayableImage = ImageTransforms.createPixelTransformation(
                    rgbImage,
                    (rgb, p) -> p.set(rgb.getInteger()),
                    new ARGBType(0)
            );
            ImageJFunctions.show(displayableImage);
        }
    }

    public static <T extends NumericType<T>> void displayNumericImage(RandomAccessibleInterval<T> img) {
        if (DISPLAY_TEST_IMAGES) {
            ImageJFunctions.show(img);
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
        while(refImageCursor.hasNext()) {
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

}
