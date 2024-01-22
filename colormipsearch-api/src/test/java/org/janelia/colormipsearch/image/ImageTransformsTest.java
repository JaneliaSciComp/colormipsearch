package org.janelia.colormipsearch.image;

import java.io.IOException;

import io.scif.img.ImgOpener;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.IntARGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.junit.Test;
import org.scijava.io.location.FileLocation;

public class ImageTransformsTest {

    @Test
    public void mirrorImage() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            ImageAccess<? extends RGBPixelType<?>> mirroredTestImage = ImageTransforms.createGeomTransformation(testImage, new MirrorTransform(testImage.getImageShape(), 0));
            ImageJFunctions.show((RandomAccessibleInterval) testImage);
            ImageJFunctions.show((RandomAccessibleInterval) mirroredTestImage);
        }
    }

    @Test
    public void maxFilter() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw" + (i % 2 + 1) + ".tif";
            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            long startTime = System.currentTimeMillis();
            ImageAccess<? extends RGBPixelType<?>> maxFilterTestImage = ImageTransforms.createHyperSphereDilationTransformation(testImage, 10);
            ImageJFunctions.show(new SimpleImageAccess<>(
                    new ConvertPixelAccess<>(
                            testImage.randomAccess(),
                            rgb -> new ARGBType(ARGBType.rgba(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255))
                    ),
                    testImage,
                    new ARGBType(ARGBType.rgba(0, 0, 0, 255))
            ));

            ImageJFunctions.show(new SimpleImageAccess<>(
                    new ConvertPixelAccess<>(
                            (RandomAccess<RGBPixelType<?>>) maxFilterTestImage.randomAccess(),
                            rgb -> new ARGBType(ARGBType.rgba(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255))
                    ),
                    maxFilterTestImage,
                    new ARGBType(ARGBType.rgba(0, 0, 0, 255))
            ));
            long completedTime = System.currentTimeMillis();
            System.out.println("Completed maxFilter for " + testFileName + " in " + (completedTime-startTime)/1000.);
        }
    }

}
