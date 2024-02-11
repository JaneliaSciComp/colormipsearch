package org.janelia.colormipsearch.image;

import java.io.IOException;

import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.filter.RankFilters;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageTransformsTest {

    @Test
    public void mirrorImage() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            long startTime = System.currentTimeMillis();
            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            ImageAccess<ByteArrayRGBPixelType> mirroredTestImage = ImageTransforms.createGeomTransformation(testImage, new MirrorTransform(testImage.getImageShape(), 0));
            ImageAccess<ByteArrayRGBPixelType> doubleMirroredTestImage = ImageTransforms.createGeomTransformation(mirroredTestImage, new MirrorTransform(mirroredTestImage.getImageShape(), 0));

            TestUtils.compareImages(testImage, doubleMirroredTestImage,
                    (v1, v2) -> {
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
                    });
            long endTime = System.currentTimeMillis();
            System.out.println("Completed mirror for " + testFileName + " in " + (endTime-startTime)/1000.);
            TestUtils.displayRGBImage(testImage);
            TestUtils.displayRGBImage(mirroredTestImage);
            TestUtils.displayRGBImage(doubleMirroredTestImage);
        }
    }

    @Test
    public void maxFilter() {
        int testRadius = 20;
        for (int i = 0; i < 1; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            long startTime = System.currentTimeMillis();
            ImageAccess<ByteArrayRGBPixelType> maxFilterRGBTestImage = ImageTransforms.createHyperSphereDilationTransformation(
                    testImage, testRadius
            );
            Img<ByteArrayRGBPixelType> maxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    maxFilterRGBTestImage,
                    new ByteArrayRGBPixelType()
            );
            long endTime = System.currentTimeMillis();

            ImagePlus refImage = new Opener().openTiff(testFileName, 1);
            RankFilters maxFilter = new RankFilters();
            long maxFilterStartTime = System.currentTimeMillis();
            // IJ1 creates the circular kernel a bit differently by qdding 1e-10 to the radius
            // so in order for my test to work I subtract a very small value (1e-10) from the test radius
            maxFilter.rank(refImage.getProcessor(), testRadius - 1e-10, RankFilters.MAX);
            long maxFilterEndTime = System.currentTimeMillis();

            TestUtils.displayIJImage(refImage);
            TestUtils.displayRGBImage(maxFilterRGBTestImage);

            int ndiffs = 0;
            for (int r = 0; r < refImage.getHeight(); r++) {
                for (int c = 0; c < refImage.getWidth(); c++) {
                    int refPixel = refImage.getProcessor().get(c, r) & 0xffffff;
                    int testPixel = maxFilterImg.getAt(c, r).getInteger() & 0xffffff;
                    if (refPixel != testPixel) {
                        ndiffs++;
                    }
                }
            }
//            assertEquals("Pixel differences", 0, ndiffs);
            System.out.printf("Completed maxFilter for %s in %f vs %f using IJ1 rankFilter\n",
                    testFileName,
                    (endTime-startTime) / 1000.,
                    (maxFilterEndTime-maxFilterStartTime) / 1000.);
        }
        try {
            System.in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
