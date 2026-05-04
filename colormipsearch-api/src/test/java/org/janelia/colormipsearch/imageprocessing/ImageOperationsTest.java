package org.janelia.colormipsearch.imageprocessing;

import ij.plugin.filter.RankFilters;
import ij.process.ImageProcessor;
import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.image.ByteImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class ImageOperationsTest {
    private static final Logger LOG = LoggerFactory.getLogger(ImageOperationsTest.class);

    @Test
    public void highExpressionMaskOperations() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        ImageArray testImageArray = ImageReader.readImageArrayFromFile(testImageName);

        int r1 = 60;
        int r2 = 20;

        ImageArray testImageArrayNoLabels = ImageOperations.maskRegion(testImageArray, ImageTestUtils.getExcludedRegionsPredicate());
        TestUtils.displayImage(testImageArrayNoLabels, "Image with cleared labels");

        // Max filter at two radii
        long startMask20 = System.currentTimeMillis();
        ImageArray mask20 = ImageOperations.maxRGBFilter2D(testImageArrayNoLabels, r2, r2);
        long endMask20 = System.currentTimeMillis();
        TestUtils.displayImage(mask20, "20px dilation");
        long startMask60 = System.currentTimeMillis();
        ImageArray mask60 = ImageOperations.maxRGBFilter2D(testImageArrayNoLabels, r1, r1);
        long endMask60 = System.currentTimeMillis();
        TestUtils.displayImage(mask60, "60px dilation");

        // Subtract: keep pixels from the 60x image that are NOT in the 20x image
        ImageArray diff = ImageOperations.duplicateImage(
                ImageOperations.combine2(mask60, mask20,
                (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0 : p1),
            ByteImageArray::new
        );

        TestUtils.displayImage(diff, "Overexpression");
        // Convert to gray -> binary mask
        long startBinaryMask = System.currentTimeMillis();
        ImageArray gray = ImageOperations.rgbToGray8(diff);
        ImageArray signal = ImageOperations.binaryMask(gray, 0);
        long endBinaryMask = System.currentTimeMillis();

        // Count non-zero pixels
        int nonZeroPxs = 0;
        for (int i = 0; i < signal.getSpatialSize(); i++) {
            if (signal.getPackedIntValAtIndex(i) != 0)
                nonZeroPxs++;
        }
        long endCounting = System.currentTimeMillis();
        LOG.info("High expressed region: {} pixels completed 20px dilation: {}secs, 60 px dilation: {}secs, binary mask: {}secs, coounting: {}secs",
                nonZeroPxs,
                (endMask20-startMask20)/1000.,
                (endMask60-startMask60)/1000.,
                (endBinaryMask-startBinaryMask)/1000.,
                (endCounting-endBinaryMask)/1000.);
        assertEquals(94330, nonZeroPxs);
        TestUtils.waitForKey();
    }

    @Test
    public void maxFilterForGrayImage() {
        final int radius = 10;

        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif";
        ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

        // Convert to gray for our code
        ImageArray grayImage = ImageOperations.rgbToGray8(testMIP);
        ImageArray maxFilteredImage = ImageOperations.maxGrayFilter3D(grayImage, radius, radius, 0);

        // IJ1 reference
        ImageProcessor asByteProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getDepth(), testMIP.getChannels()).convertToByte(true);
        new RankFilters().rank(asByteProcessor, radius, RankFilters.MAX);

        for (int i = 0; i < asByteProcessor.getPixelCount(); i++) {
            assertEquals(asByteProcessor.get(i), maxFilteredImage.getPackedIntValAtIndex(i));
        }
    }

    @Test
    public void maxFilterForRGBImage2D() {
        final int radius = 10;
        RankFilters rankFilters = new RankFilters();

        for (int i = 1; i < 5; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);
            ImageProcessor refImageProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());

            long startTime = System.currentTimeMillis();
            ImageArray maxFilteredImage = ImageOperations.maxRGBFilter2D(testMIP, radius, radius);
            long endMaxFilterTime = System.currentTimeMillis();
            // IJ1 maxFilter
            rankFilters.rank(refImageProcessor, radius - 1e-10, RankFilters.MAX);
            long endIJ1MaxFilterTime = System.currentTimeMillis();
            int ndiffs = TestUtils.countDiffs(maxFilteredImage, TestUtils.ij1ProcessorToImageArray(refImageProcessor, ByteImageArray::new));
            LOG.info("MaxFilter time {} vs {} - IJ1 maxFilter time - found {} diffs",
                    (endMaxFilterTime - startTime) / 1000.,
                    (endIJ1MaxFilterTime - endMaxFilterTime) / 1000.,
                    ndiffs);
            assertEquals(testImageName, 0, ndiffs);
        }
    }

}
