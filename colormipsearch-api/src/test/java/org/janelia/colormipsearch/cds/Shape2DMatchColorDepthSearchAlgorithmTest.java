package org.janelia.colormipsearch.cds;

import java.util.Collections;
import java.util.function.BiPredicate;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.mips.GrayImageLoader;
import org.janelia.colormipsearch.mips.RGBImageLoader;
import org.janelia.colormipsearch.model.FileData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowTests.class)
public class Shape2DMatchColorDepthSearchAlgorithmTest {

    private static final Logger LOG = LoggerFactory.getLogger(Shape2DMatchColorDepthSearchAlgorithmTest.class);

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

        RandomAccessibleInterval<UnsignedByteType> signalMask = ImageTransforms.rgbToSignalTransformation(maskForRegionsWithTooMuchExpression, 0);
        long n = ImageAccessUtils.fold(signalMask,
                0L, (a, p) -> a + p.get(), Long::sum);
        long endTime = System.currentTimeMillis();
        LOG.info("Completed calculating {} pixel mask for regions with high expression for {} in {} - total: {} secs",
                n, testFileName,
                (endTime1 - startTime) / 1000.,
                (endTime - startTime) / 1000.);
        assertTrue(n > 0);
    }

    @Test
    public void computeShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/0342_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02.png";
        String fullMask = "src/test/resources/colormipsearch/api/cdsearch/MAX_JRC2018_UNISEX_20x_HR_2DMASK.tif";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        long start = System.currentTimeMillis();
        RandomAccessibleInterval<ByteArrayRGBPixelType> queryImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(emCDM));
        RandomAccessibleInterval<UnsignedByteType> roiMask = new GrayImageLoader<>(alignmentSpace, new UnsignedByteType()).loadImage(FileData.fromString(fullMask));

        long[] dims = queryImage.dimensionsAsLongArray();
        BiPredicate<long[], ByteArrayRGBPixelType> isScaleOrLabelRegion = (pos, pix) -> SCALE_OR_LABEL_COND.test(pos, dims);
        Shape2DMatchColorDepthSearchAlgorithm shapeScoreAlg = new Shape2DMatchColorDepthSearchAlgorithm(
                queryImage,
                roiMask,
                isScaleOrLabelRegion,
                20,
                20,
                true,
                10
        );
        long endInit = System.currentTimeMillis();
        RandomAccessibleInterval<ByteArrayRGBPixelType> targetImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(lmCDM));
        ShapeMatchScore shapeMatchScore =  shapeScoreAlg.calculateMatchingScore(
                targetImage,
                Collections.emptyMap()
        );

        long end = System.currentTimeMillis();
        assertNotNull(shapeMatchScore);
        assertTrue(shapeMatchScore.getGradientAreaGap() != -1);

        LOG.info("Completed bidirectional shape score ({}, {}) init in {} secs, score in {} secs, total {} secs - mem used {}M",
                shapeMatchScore.getGradientAreaGap(),
                shapeMatchScore.getHighExpressionArea(),
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));
    }
}
