package org.janelia.colormipsearch.image;

import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class TestUtils {

    public static <T extends RGBPixelType<T>> void displayRGBImage(ImageAccess<T> rgbImage) {
        ImageJFunctions.show(
                ImageTransforms.<T, ARGBType>createPixelTransformation(
                    rgbImage,
                    rgb -> new ARGBType(ARGBType.rgba(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255))
                )
        );
    }
}
