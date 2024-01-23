package org.janelia.colormipsearch.image;

import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

public class ImageTransformsTest {

    @Test
    public void mirrorImage() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            ImageAccess<ByteArrayRGBPixelType> mirroredTestImage = ImageTransforms.createGeomTransformation(testImage, new MirrorTransform(testImage.getImageShape(), 0));
            TestUtils.displayRGBImage(testImage);
            TestUtils.displayRGBImage(mirroredTestImage);
        }
        try {
            System.in.read();
        } catch (Exception e) {}
    }

    @Test
    public void maxFilter() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw" + (i % 2 + 1) + ".tif";
            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            long startTime = System.currentTimeMillis();
            ImageAccess<ByteArrayRGBPixelType> maxFilterTestImage = ImageTransforms.createHyperSphereDilationTransformation(
                    testImage, 10
            );
            TestUtils.displayRGBImage(maxFilterTestImage);
            long completedTime = System.currentTimeMillis();
            System.out.println("Completed maxFilter for " + testFileName + " in " + (completedTime-startTime)/1000.);
        }
        try {
            System.in.read();
        } catch (Exception e) {}
    }

}
