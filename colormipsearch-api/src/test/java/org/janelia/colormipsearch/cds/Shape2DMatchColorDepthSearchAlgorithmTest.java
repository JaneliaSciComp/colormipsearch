package org.janelia.colormipsearch.cds;

import java.util.function.BiPredicate;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Shape2DMatchColorDepthSearchAlgorithmTest {

    private static final BiPredicate<long[]/*pos*/, long[]/*shape*/> SCALE_OR_LABEL_COND = (long[] pos, long[] shape) -> {
        if (pos.length != shape.length) {
            throw new IllegalArgumentException("Image coordinates and dimensions must be equal");
        }
        if (pos.length != 2) {
            throw new IllegalArgumentException("Image must be a 2D-image");
        }
        long imgWidth = shape[0];
        long x = pos[0];
        long y = pos[1];
        boolean isInsideColorScale = imgWidth > 270 && x >= imgWidth - 270 && y < 90;
        boolean isInsideNameLabel = x < 330 && y < 100;
        return isInsideColorScale || isInsideNameLabel;
    };

    @Test
    public void overExpressesMaskExpression() {
        long startTime = System.currentTimeMillis();
        String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        Img<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
        BiPredicate<long[], ByteArrayRGBPixelType> isScaleOrLabelRegion = (pos, pix) -> SCALE_OR_LABEL_COND.test(pos, testImage.dimensionsAsLongArray());
        RandomAccessibleInterval<ByteArrayRGBPixelType> testImageWithMaskedLabels = ImageTransforms.maskPixelsMatchingCond(testImage, isScaleOrLabelRegion, null);

        TestUtils.displayRGBImage(testImage);
        RandomAccessibleInterval<ByteArrayRGBPixelType> maskForRegionsWithTooMuchExpression =
                Shape2DMatchColorDepthSearchAlgorithm.createMaskForPotentialRegionsWithHighExpression(
                        testImageWithMaskedLabels,
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
