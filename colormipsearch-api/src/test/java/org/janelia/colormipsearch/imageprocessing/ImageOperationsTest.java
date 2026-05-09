package org.janelia.colormipsearch.imageprocessing;

import java.util.Arrays;

import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.Gray8ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class ImageOperationsTest {
    private static final Logger LOG = LoggerFactory.getLogger(ImageOperationsTest.class);

    @Test
    public void highExpressionMaskOperationsDuplicatingEachStep() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        ImageArray testImageArray = ImageReader.readImageArrayFromFile(testImageName);

        ImageArray testImageArrayNoLabels = ImageOperations.duplicateImage(
                ImageOperations.maskRegion(testImageArray, ImageTestUtils.getExcludedRegionsPredicate()),
                RGBByteImageArray::new
        );
        TestUtils.displayImage(testImageArrayNoLabels, "Image with cleared labels");

        int r1 = 60;
        int r2 = 20;
        // Max filter at two radii
        LOG.debug("Start {} px dilation", r1);
        long startR1Dilation = System.currentTimeMillis();
        ImageArray r1Dilation = ImageOperations.duplicateImage(
                ImageOperations.rgbMaxFilter2D(testImageArrayNoLabels, r1, r1),
                RGBByteImageArray::new
        );
        long endR1Dilation = System.currentTimeMillis();
        TestUtils.displayImage(r1Dilation, "R1 dilation");

        LOG.debug("Start {} px dilation", r2);
        long startR2Dilation = System.currentTimeMillis();
        ImageArray r2Dilation = ImageOperations.duplicateImage(
                ImageOperations.rgbMaxFilter2D(testImageArrayNoLabels, r2, r2),
                RGBByteImageArray::new
        );
        long endR2Dilation = System.currentTimeMillis();
        TestUtils.displayImage(r2Dilation, "R2 dilation");

        // Subtract: keep pixels from the 60x image that are NOT in the 20x image
        LOG.debug("Start diff");
        long startDiff = System.currentTimeMillis();
        ImageArray diff = ImageOperations.combine2(
                r1Dilation, r2Dilation,
                (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1
        );
        long endDiff = System.currentTimeMillis();

        TestUtils.displayImage(diff, "R1 dilation - R2 dilation");

        // Convert to gray -> binary mask
        long startBinaryMask = System.currentTimeMillis();
        ImageArray diffGray = ImageOperations.rgbToGray8(diff);
        ImageArray diffBinary = ImageOperations.binaryMask(diffGray, 0, 255);
        long endBinaryMask = System.currentTimeMillis();

        TestUtils.displayImage(diffBinary, "Binary R1 dilation - R2 dilation");

        // Count non-zero pixels
        int nonZeroPxs = 0;
        for (int i = 0; i < diffBinary.getSpatialSize(); i++) {
            if (diffBinary.getPackedIntValAtIndex(i) != 0)
                nonZeroPxs++;
        }
        long endCounting = System.currentTimeMillis();
        LOG.info("High expressed region: {} pixels completed 20px dilation: {} secs, 60 px dilation: {} secs, diff: {} secs, binary mask: {} secs, counting: {} secs",
                nonZeroPxs,
                (endR2Dilation - startR2Dilation) / 1000.,
                (endR1Dilation - startR1Dilation) / 1000.,
                (endDiff - startDiff) / 1000.,
                (endBinaryMask - startBinaryMask) / 1000.,
                (endCounting - endBinaryMask) / 1000.);
        assertEquals(94298, nonZeroPxs);
    }

    @Test
    public void highExpressionMaskOperationsDuplicateFinalMaskOnly() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        ImageArray testImageArray = ImageReader.readImageArrayFromFile(testImageName);

        ImageArray testImageArrayNoLabels = ImageOperations.maskRegion(testImageArray, ImageTestUtils.getExcludedRegionsPredicate());

        int r1 = 60;
        int r2 = 20;
        long startHighExpressionMask = System.currentTimeMillis();
        ImageArray r1Dilation = ImageOperations.rgbMaxFilter2D(testImageArrayNoLabels, r1, r1);
        ImageArray r2Dilation = ImageOperations.rgbMaxFilter2D(testImageArrayNoLabels, r2, r2);
        // Subtract: keep pixels from the 60x image that are NOT in the 20x image
        ImageArray diff = ImageOperations.duplicateImage(
                ImageOperations.combine2(
                        r1Dilation, r2Dilation,
                        (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1
                ),
                RGBByteImageArray::new
        );
        long endHighExpressionMask = System.currentTimeMillis();

        // Convert to gray -> binary mask
        long startBinaryMask = System.currentTimeMillis();
        ImageArray diffGray = ImageOperations.rgbToGray8(diff);
        ImageArray diffBinary = ImageOperations.binaryMask(diffGray, 0, 255);

        // Count non-zero pixels
        int nonZeroPxs = 0;
        for (int i = 0; i < diffBinary.getSpatialSize(); i++) {
            if (diffBinary.getPackedIntValAtIndex(i) != 0)
                nonZeroPxs++;
        }
        long endBinaryMask = System.currentTimeMillis();

        LOG.info("High expressed region: {} pixels completed {} secs, binary mask and counting: {} secs",
                nonZeroPxs,
                (endHighExpressionMask - startHighExpressionMask) / 1000.,
                (endBinaryMask - startBinaryMask) / 1000.);
        assertEquals(94298, nonZeroPxs);
    }

    @Test
    public void maxFilterForGrayImage() {
        final int radius = 10;

        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif";
        ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);
        ImageProcessor testMIPIJProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());

        // Convert to gray for our code
        long startTime = System.currentTimeMillis();
        ImageArray grayImage = ImageOperations.rgbToGray8(testMIP);
        ImageArray maxFilteredImage = ImageOperations.duplicateImage(
                ImageOperations.gray8MaxFilter3D(grayImage, radius, radius, 0),
                Gray8ImageArray::new
        );
        long endMaxFilter = System.currentTimeMillis();
        // IJ1 reference
        ImageProcessor asByteProcessor = testMIPIJProcessor.convertToByte(true);
        new RankFilters().rank(asByteProcessor, radius - 1e-10, RankFilters.MAX);
        long endIJ1MaxFilter = System.currentTimeMillis();

        int ndiffs = TestUtils.countDiffs(maxFilteredImage, asByteProcessor);
        LOG.info("Max filter gray finished in {}secs and in {}secs with IJ1 filter - found {} diffs",
                (endMaxFilter-startTime) / 1000.,
                (endIJ1MaxFilter-endMaxFilter) / 1000.,
                ndiffs);
        TestUtils.displayImage(testMIP, "Source image");
        TestUtils.displayImage(grayImage, "Gray8");
        TestUtils.displayImage(maxFilteredImage, "Gray8 max filter");
        TestUtils.displayImageProcessor(asByteProcessor, "IJ processor after rank filter");
        assertEquals(0, ndiffs);
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
            ImageArray maxFilteredImage = ImageOperations.duplicateImage(
                    ImageOperations.rgbMaxFilter2D(testMIP, radius, radius),
                    RGBByteImageArray::new
            );
            long endMaxFilterTime = System.currentTimeMillis();
            // IJ1 maxFilter
            rankFilters.rank(refImageProcessor, radius, RankFilters.MAX);
            long endIJ1MaxFilterTime = System.currentTimeMillis();
            int ndiffs = TestUtils.countDiffs(maxFilteredImage, refImageProcessor);
            LOG.info("MaxFilter time {} vs {} - IJ1 maxFilter time - found {} diffs",
                    (endMaxFilterTime - startTime) / 1000.,
                    (endIJ1MaxFilterTime - endMaxFilterTime) / 1000.,
                    ndiffs);
            assertEquals(testImageName, 0, ndiffs);
        }
    }

    @Test
    public void maxFilterThenHorizontalFlipRGB2DImage() {
        final int radius = 10;
        RankFilters maxFilter = new RankFilters();

        for (int i = 1; i < 6; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);
            ImageProcessor refImageProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());

            long startImageArrayOps = System.currentTimeMillis();
            ImageArray flippedMaxFilteredImage = ImageOperations.duplicateImage(
                    ImageOperations.flipImage(
                            ImageOperations.rgbMaxFilter2D(testMIP, radius, radius),
                            Dimensions.X_AXIS
                    ),
                    RGBByteImageArray::new
            );
            long endImageArrayOps = System.currentTimeMillis();
            maxFilter.rank(refImageProcessor, radius, RankFilters.MAX);
            refImageProcessor.flipHorizontal();
            long endIJ1Ops = System.currentTimeMillis();

            int ndiffs = TestUtils.countDiffs(flippedMaxFilteredImage, refImageProcessor);

            long endDiff = System.currentTimeMillis();
            LOG.info("FlippedMaxFilter imageArray time {} vs {} - IJ1 maxFilter and flip time - found {} diffs in {}",
                    (endImageArrayOps - startImageArrayOps) / 1000.,
                    (endIJ1Ops - endImageArrayOps) / 1000.,
                    ndiffs,
                    (endDiff - endIJ1Ops) / 1000.);
            assertEquals(testImageName, 0, ndiffs);
        }
    }

    @Test
    public void horizontalFlipThenMaxFilterRGB2DImage() {
        final int radius = 10;
        RankFilters maxFilter = new RankFilters();

        for (int i = 1; i < 6; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);
            ImageProcessor refImageProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());

            long startImageArrayOps = System.currentTimeMillis();
            ImageArray flippedMaxFilteredImage = ImageOperations.duplicateImage(
                    ImageOperations.rgbMaxFilter2D(
                            ImageOperations.flipImage(testMIP, Dimensions.X_AXIS),
                            radius,
                            radius),
                    RGBByteImageArray::new
            );
            long endImageArrayOps = System.currentTimeMillis();
            refImageProcessor.flipHorizontal();
            maxFilter.rank(refImageProcessor, radius, RankFilters.MAX);
            long endIJ1Ops = System.currentTimeMillis();

            int ndiffs = TestUtils.countDiffs(flippedMaxFilteredImage, TestUtils.ij1ProcessorToImageArray(refImageProcessor,RGBByteImageArray::new));

            long endDiff = System.currentTimeMillis();
            LOG.info("MaxFilterFlipped imageArray time {} vs {} - IJ1 flip and maxFilter time - found {} diffs in {}",
                    (endImageArrayOps - startImageArrayOps) / 1000.,
                    (endIJ1Ops - endImageArrayOps) / 1000.,
                    ndiffs,
                    (endDiff - endIJ1Ops) / 1000.);
            assertEquals(testImageName, 0, ndiffs);
        }
    }

    @Category({SlowTests.class})
    @Test
    public void maxFilter3DEntireImage() {
        class TestData {
            final String fn;
            final int[] radii;
            final int minVal;
            final int maxVal;
            final int nonBgCount;
            final int meanVal;

            TestData(String fn, int[] radii,  int minVal, int maxVal, int nonBgCount, int meanVal) {
                this.fn = fn;
                this.radii = radii;
                this.minVal = minVal;
                this.maxVal = maxVal;
                this.nonBgCount = nonBgCount;
                this.meanVal = meanVal;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[] {7, 7, 4},
                        1,
                        530,
                        274501,
                        151
                ),
        };
        for (TestData td : testData) {
            ImageArray testImage = ImageReader.readImageArrayFromFile(td.fn);
            LOG.info("Begin max filter for {} with radii: {}", td.fn, Arrays.toString(td.radii));
            long startTime = System.currentTimeMillis();
            ImageArray maxFilterTestImage = ImageOperations.duplicateImage(
                    ImageOperations.gray16MaxFilter3D(testImage, td.radii[0], td.radii[1], td.radii[2]),
                    Gray16ImageArray::new
            );
            long endMaxFilterTime = System.currentTimeMillis();
            ImageStats maxFilterStats = ImageOperations.getImageStats(maxFilterTestImage);
            LOG.info("Complete {} maxFilter with radii {} in {} secs - image stats: {}",
                    td.fn,
                    Arrays.toString(td.radii),
                    (endMaxFilterTime - startTime) / 1000.,
                    maxFilterStats
            );
            TestUtils.displayImage(maxFilterTestImage, "Max filter " + td.fn);
            assertEquals(td.minVal, maxFilterStats.minVal);
            assertEquals(td.maxVal, maxFilterStats.maxVal);
            assertEquals(td.nonBgCount, maxFilterStats.nonBgCount);
            assertEquals(td.meanVal, maxFilterStats.meanVal);
        }
    }

    @Test
    public void convertToGray() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif";

        ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);
        ImageProcessor refImageProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());
        ImagePlus refImage = new ImagePlus("Ref Image", refImageProcessor);

        ImageConverter ic = new ImageConverter(refImage);

        long start = System.currentTimeMillis();
        ImageArray grayImage = ImageOperations.rgbToGray8(testMIP);
        long endImageArrayOps = System.currentTimeMillis();

        ic.convertToGray8();
        long endIJ1Ops = System.currentTimeMillis();

        ImageProcessor convertedImageProcessor = refImage.getProcessor();
        int ndiffs = TestUtils.countDiffs(grayImage, convertedImageProcessor);
        long endDiff = System.currentTimeMillis();

        LOG.info("Convert RGB to gray imageArray time {} vs {} - IJ1 convert time - found {} diffs in {}",
                (endImageArrayOps - start) / 1000.,
                (endIJ1Ops - endImageArrayOps) / 1000.,
                ndiffs,
                (endDiff - endIJ1Ops) / 1000.);
    }
}
