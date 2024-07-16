package org.janelia.colormipsearch.cds;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Shape2DMatchColorDepthSearchAlgorithmTest {

    @Test
    public void overExpressesMaskExpression() {
        long startTime = System.currentTimeMillis();
        String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        Img<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
        TestUtils.displayRGBImage(testImage);
        RandomAccessibleInterval<ByteArrayRGBPixelType> maskForRegionsWithTooMuchExpression =
                Shape2DMatchColorDepthSearchAlgorithm.createMaskForPotentialRegionsWithHighExpression(
                        testImage,
                        new ByteArrayRGBPixelType(),
                        60, 20
                );
        long endTime1 = System.currentTimeMillis();

        TestUtils.displayRGBImage(maskForRegionsWithTooMuchExpression);

        long endTime2 = System.currentTimeMillis();

        RandomAccessibleInterval<UnsignedByteType> signalMask = ImageTransforms.rgbToSignalTransformation(maskForRegionsWithTooMuchExpression, 0);
        long n = ImageAccessUtils.fold(signalMask,
                0L, (a, p) -> a + p.get(), Long::sum);
        long endTime = System.currentTimeMillis();
        System.out.printf("Completed calculating %d pixel mask for regions with high expression for %s in %f -  %f - final: %fs\n",
                n, testFileName,
                (endTime1 - startTime) / 1000.,
                (endTime2 - startTime) / 1000.,
                (endTime - startTime) / 1000.);
        assertTrue(n > 0);
    }

}
