package org.janelia.colormipsearch.image;

import java.util.Comparator;

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

    public static <T extends RGBPixelType<T>> void displayRGBImage(ImageAccess<T> rgbImage) {
//        if (Boolean.getBoolean("display.testImages")) {
            ImageAccess<ARGBType> displayableImage = ImageTransforms.createPixelTransformation(
                    rgbImage,
                    rgb -> new ARGBType(ARGBType.rgba(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255))
            );
            ImageJFunctions.show(displayableImage);
//        }
    }

    public static <T extends NumericType<T>> void displayNumericImage(RandomAccessibleInterval<T> img) {
//        if (Boolean.getBoolean("display.testImages")) {
        ImageJFunctions.show(img);
//        }
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
