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

    private static final boolean DISPLAY_TEST_IMAGES = Boolean.getBoolean("display.testImages");

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

    public static <T> void compareImages(RandomAccessibleInterval<T> refImage, RandomAccessibleInterval<T> testImage, Comparator<T> comparator) {
        assertArrayEquals(refImage.dimensionsAsLongArray(), testImage.dimensionsAsLongArray());
        assertNotSame(refImage, testImage);
        Cursor<T> refImageCursor = new RandomAccessibleIntervalCursor<>(refImage);
        while(refImageCursor.hasNext()) {
            refImageCursor.fwd();
            T refPixel = refImageCursor.get();
            T testPixel = testImage.randomAccess().setPositionAndGet(refImageCursor.positionAsLongArray());
            assertEquals(0, comparator.compare(refPixel, testPixel));
        }
    }

}
