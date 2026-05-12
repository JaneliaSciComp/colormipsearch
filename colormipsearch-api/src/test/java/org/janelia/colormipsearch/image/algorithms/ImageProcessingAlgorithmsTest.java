package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.image.ImageStats;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class ImageProcessingAlgorithmsTest {

    private static final Logger LOG = LoggerFactory.getLogger(ImageProcessingAlgorithmsTest.class);

    @Test
    public void distanceTransformNoDilation() {
        class TestData {
            final String fn;

            TestData(String fn) {
                this.fn = fn;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif"
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif"
                ),
        };
        for (TestData td : testData) {
            ImageArray testImage = ImageReader.readImageArrayFromFile(td.fn);
            long startTime = System.currentTimeMillis();
            ImageArray algDTImg = DistanceTransformAlgorithm.generateDistanceTransformWithoutDilation(testImage, 1);
            long endDTTime = System.currentTimeMillis();

            TestUtils.displayImage(algDTImg, "DT Image " + td.fn);

            LOG.info("Complete {} distance transform in {} secs",
                    td.fn,
                    (endDTTime - startTime) / 1000.
            );
        }
    }

    @Test
    public void distanceTransformWithDilation() {
        class TestData {
            final String cdmFn;
            final String gradFn;
            final int radius;

            TestData(String cdmFn, String gradFn, int radius) {
                this.cdmFn = cdmFn;
                this.gradFn = gradFn;
                this.radius = radius;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/grad/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.png",
                        20
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        20
                ),
        };
        for (TestData td : testData) {
            ImageArray cdmImage = ImageOperations.maskRegion(
                    ImageOperations.maskRGB(ImageReader.readImageArrayFromFile(td.cdmFn), 100),
                    ImageTestUtils.getExcludedRegionsPredicate()
            );
            ImageArray gradImage = ImageReader.readImageArrayFromFile(td.gradFn);
            long startTime = System.currentTimeMillis();
            ImageArray dtImageWithDilation = DistanceTransformAlgorithm.generateDistanceTransform(cdmImage, td.radius, 1);
            long endDT = System.currentTimeMillis();
            ImageArray cdmDilation = ImageOperations.duplicateImage(
                    ImageOperations.rgbMaxFilter2D(cdmImage, td.radius, td.radius),
                    RGBByteImageArray::new
            );
            ImageArray dtFromDilation = DistanceTransformAlgorithm.generateDistanceTransformWithoutDilation(cdmDilation, 1);
            long endDilationAndDT = System.currentTimeMillis();

            TestUtils.displayImage(gradImage, td.gradFn);
            TestUtils.displayImage(dtImageWithDilation, "DT Image With dilation "  + td.cdmFn);
            TestUtils.displayImage(dtFromDilation, "DT from dilation " + td.cdmFn);

            int ndiffs1 = TestUtils.countDiffs(dtImageWithDilation, gradImage);
            int ndiffs2 = TestUtils.countDiffs(dtFromDilation, gradImage);
            int ndiffs = TestUtils.countDiffs(dtImageWithDilation, dtFromDilation);

            LOG.info("Complete {} distance transform with dilation in {} secs, dilation and distance transform in {} secs - " +
                            "found {} and {} diffs when compared to pre-computed grad and {} diffs between them",
                    td.cdmFn,
                    (endDT - startTime) / 1000.,
                    (endDilationAndDT - endDT) / 1000.,
                    ndiffs1,
                    ndiffs2,
                    ndiffs
            );
        }
    }

    @Category({SlowTests.class})
    @Test
    public void connectedComponents() {
        class TestData {
            final String fn;
            final int[] radii;
            final int threshold;
            final int minVol;
            final int minVal;
            final int maxVal;
            final int meanVal;
            final int nonBgCount;

            TestData(String fn, int[] radii, int threshold, int minVol,
                     int minVal, int maxVal, int meanVal, int nonBgCount) {
                this.fn = fn;
                this.radii = radii;
                this.threshold = threshold;
                this.minVol = minVol;
                this.minVal = minVal;
                this.maxVal = maxVal;
                this.meanVal = meanVal;
                this.nonBgCount = nonBgCount;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[] {7, 7, 4},
                        25,
                        300,
                        25,
                        530,
                        155,
                        265631
                ),
        };
        for (TestData td : testData) {
            ImageArray testImage = ImageReader.readImageArrayFromFile(td.fn);
            long startTime = System.currentTimeMillis();
            ImageArray dilatedTestImage = ImageOperations.duplicateImage(
                    ImageOperations.gray16MaxFilter3D(
                            testImage,
                            td.radii[0], td.radii[1], td.radii[2]
                    ),
                    Gray16ImageArray::new
            );
            TestUtils.displayImage(dilatedTestImage, "Dilated " + td.fn);
            long endMaxFilterTime = System.currentTimeMillis();
            Connect3DComponentsAlgorithm.ComponentsResult componentsResult = Connect3DComponentsAlgorithm.findConnectedComponents(
                    dilatedTestImage,
                    td.threshold
            );
            assertEquals(td.nonBgCount, componentsResult.getLargestComponentSize());
            ImageArray connectedComponentsTestImage = ImageOperations.maskRegion(
                    dilatedTestImage,
                    new Connect3DComponentsAlgorithm.ComponentLabelRegionPredicate(componentsResult.getLabels(), componentsResult.getLargestLabel())
            );
            long endConnectedCompsTime = System.currentTimeMillis();
            TestUtils.displayImage(connectedComponentsTestImage, "Connected comps " + td.fn);
            ImageStats imageStats = ImageOperations.getImageStats(connectedComponentsTestImage);
            LOG.info("Complete {} dilation in {} secs and connected components in {} secs - image stats: {}",
                    td.fn,
                    (endMaxFilterTime - startTime) / 1000.,
                    (endConnectedCompsTime - endMaxFilterTime) / 1000.,
                    imageStats
            );
            assertEquals(td.minVal,  imageStats.minVal);
            assertEquals(td.maxVal,  imageStats.maxVal);
            assertEquals(td.meanVal,  imageStats.meanVal);
            assertEquals(td.nonBgCount,  imageStats.nonBgCount);
        }
    }

}
