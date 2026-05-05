package org.janelia.colormipsearch.cds;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.image.Gray8ByteImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Shape2DMatchColorDepthSearchAlgorithmTest {

    private static final Logger LOG = LoggerFactory.getLogger(Shape2DMatchColorDepthSearchAlgorithmTest.class);

    @Test
    public void overExpressesMaskExpression() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif";

        ImageMaskPredicate excludedRegionsPredicate = ImageTestUtils.getExcludedRegionsPredicate();
        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(emCDM);
        ImageArray queryImageWithNoLabels = ImageOperations.duplicateImage(
                ImageOperations.maskRegion(queryImageArray, excludedRegionsPredicate),
                RGBByteImageArray::new
        );
        long startTime = System.currentTimeMillis();
        ImageArray binaryHighExpressionQueryMask = Shape2DMatchColorDepthSearchAlgorithm.computeHighExpressionBinaryMask(
                queryImageWithNoLabels, 60, 20
        );
        int highExpressionSize = ImageOperations.countNotBg(binaryHighExpressionQueryMask);
        long highExpressionMaskEndTime = System.currentTimeMillis();
        ImageArray binaryQueryMask = ImageOperations.duplicateImage(
                ImageOperations.binaryMask(
                        ImageOperations.rgbToGray8(queryImageWithNoLabels),
                        2,
                        255
                ),
                Gray8ByteImageArray::new
        );
        int queryBinaryMaskSize = ImageOperations.countNotBg(binaryQueryMask);
        long endBinaryMask = System.currentTimeMillis();

        TestUtils.displayImage(binaryHighExpressionQueryMask, "Overexpression");
        TestUtils.displayImage(binaryQueryMask, "Binary mask");

        assertEquals(17340, queryBinaryMaskSize);
        assertEquals(70640, highExpressionSize);
        // I want to check that nothing changes on the second traversal
        long highExpressionSize2 = ImageOperations.countNotBg(binaryHighExpressionQueryMask);
        long queryBinaryMaskSize2 = ImageOperations.countNotBg(binaryQueryMask);

        long finalTime = System.currentTimeMillis();
        assertEquals(queryBinaryMaskSize, queryBinaryMaskSize2);
        assertEquals(highExpressionSize, highExpressionSize2);

        LOG.info("Computed size of high expression area ({}) and mask size ({}) in {}sec and {}sec, final check finished in {}sec",
                highExpressionSize, queryBinaryMaskSize,
                (highExpressionMaskEndTime - startTime) / 1000.,
                (endBinaryMask - highExpressionMaskEndTime) / 1000.,
                (finalTime - endBinaryMask));
    }

    @Test
    public void computeShapeScoreUsingAlgorithmProvider() {
        class TestData {
            final String emCDM;
            final String lmCDM;
            final String lmGrad;
            final long expectedGaps;
            final long expectedHighExpression;
            final long expectedScore;
            final boolean mirrored; // if true the score comes from the mirrored mask

            TestData(String emCDM, String lmCDM, String lmGrad,
                     long expectedGaps, long expectedHighExpression,
                     long expectedScore, boolean mirrored) {
                this.emCDM = emCDM;
                this.lmCDM = lmCDM;
                this.lmGrad = lmGrad;
                this.expectedGaps = expectedGaps;
                this.expectedHighExpression = expectedHighExpression;
                this.expectedScore = expectedScore;
                this.mirrored = mirrored;
            }
        }

        TestData[] testData = new TestData[]{
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        /*expectedGaps*/21365L,
                        /*expectedHighExpression*/731L,
                        /*expectedScore*/21608L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.png",
                        /*expectedGaps*/23359L,
                        /*expectedHighExpression*/523L,
                        /*expectedScore*/23533L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.png",
                        /*expectedGaps*/40696L,
                        /*expectedHighExpression*/17253L,
                        /*expectedScore*/46447L,
                        /*expectedMirrored*/true
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        /*expectedGaps*/65381L,
                        /*expectedHighExpression*/677L,
                        /*expectedScore*/65606L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.png",
                        /*expectedGaps*/104449L,
                        /*expectedHighExpression*/16803L,
                        /*expectedScore*/110050L,
                        /*expectedMirrored*/true
                ),
        };
        ImageMaskPredicate excludedRegionsPredicate = ImageTestUtils.getExcludedRegionsPredicate();
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                true,
                null,
                excludedRegionsPredicate
        );
        int testQueryThreshold = 20;
        String prevEM = null;
        ColorDepthSearchAlgorithm<ShapeMatchScore> shape2DScoreAlgorithm = null;
        for (TestData td : testData) {
            long start = System.currentTimeMillis();
            if (!td.emCDM.equals(prevEM)) {
                LOG.info("Create new score algorithm for new mask: {}", td.emCDM);
                ImageArray queryImageArray = ImageReader.readImageArrayFromFile(td.emCDM);
                shape2DScoreAlgorithm = shapeScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        queryImageArray,
                        java.util.Collections.emptyMap(),
                        testQueryThreshold,
                        0
                );
                prevEM = td.emCDM;
            }

            ImageArray targetImageArray = ImageReader.readImageArrayFromFile(td.lmCDM);
            ImageArray targetGradImageArray = ImageReader.readImageArrayFromFile(td.lmGrad);

            long endInit = System.currentTimeMillis();
            LOG.info("Initialized shape score between {} and {} in {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    (endInit - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            Map<ComputeFileType, Supplier<ImageArray>> variantSuppliers = new HashMap<ComputeFileType, Supplier<ImageArray>>() {{
                put(ComputeFileType.GradientImage, () -> targetGradImageArray);
                put(ComputeFileType.ZGapImage, () -> ImageOperations.rgbMaxFilter2D(
                        ImageOperations.maskRGB(
                                ImageOperations.maskRegion(targetImageArray, excludedRegionsPredicate),
                                testQueryThreshold
                        ),
                        10,
                        10
                ));
            }};
            ShapeMatchScore shapeMatchScore = shape2DScoreAlgorithm.calculateMatchingScore(
                    targetImageArray,
                    variantSuppliers
            );
            long end = System.currentTimeMillis();

            LOG.info("Calculated shape score between {} and {} -> {} ({}, {}, {}) in {} secs, score in {} secs, total {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    shapeMatchScore.getScore(),
                    shapeMatchScore.getGradientAreaGap(),
                    shapeMatchScore.getHighExpressionArea(),
                    shapeMatchScore.isMirrored(),
                    (endInit - start) / 1000.,
                    (end - endInit) / 1000.,
                    (end - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            assertNotNull(td.emCDM + " vs " + td.lmCDM, shapeMatchScore);
            assertTrue(td.emCDM + " vs " + td.lmCDM, shapeMatchScore.getGradientAreaGap() != -1);
            assertTrue(td.emCDM + " vs " + td.lmCDM, shapeMatchScore.getHighExpressionArea() != -1);
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedGaps, shapeMatchScore.getGradientAreaGap());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedHighExpression, shapeMatchScore.getHighExpressionArea());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedScore, shapeMatchScore.getScore());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.mirrored, shapeMatchScore.isMirrored());
        }
    }

    @Test
    public void computeShapeScoreUsingDirectAlgorithmConstructor() {
        class TestData {
            final String emCDM;
            final String lmCDM;
            final String lmGrad;
            final String lmZgap;
            final long expectedGaps;
            final long expectedHighExpression;
            final long expectedScore;
            final boolean mirrored; // if true the score comes from the mirrored mask

            TestData(String emCDM, String lmCDM, String lmGrad, String lmZGap,
                     long expectedGaps, long expectedHighExpression,
                     long expectedScore, boolean mirrored) {
                this.emCDM = emCDM;
                this.lmCDM = lmCDM;
                this.lmGrad = lmGrad;
                this.lmZgap = lmZGap;
                this.expectedGaps = expectedGaps;
                this.expectedHighExpression = expectedHighExpression;
                this.expectedScore = expectedScore;
                this.mirrored = mirrored;
            }
        }

        TestData[] testData = new TestData[]{
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        /*lmZGap*/null,
                        /*expectedGaps*/21365L,
                        /*expectedHighExpression*/731L,
                        /*expectedScore*/21608L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.png",
                        /*lmZGap*/"src/test/resources/colormipsearch/api/cdsearch/zgap/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.tif",
                        /*expectedGaps*/33884L,
                        /*expectedHighExpression*/523L,
                        /*expectedScore*/34058L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/BJD_127B01_AE_01-20171124_64_H6-40x-Brain-JRC2018_Unisex_20x_HR-2483089192251293794-CH2-01_CDM.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        /*lmZGap*/null,
                        /*expectedGaps*/23367L,
                        /*expectedHighExpression*/523L,
                        /*expectedScore*/23541L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.png",
                        /*lmZGap*/null,
                        /*expectedGaps*/40696L,
                        /*expectedHighExpression*/17253L,
                        /*expectedScore*/46447L,
                        /*expectedMirrored*/true
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.png",
                        null,
                        /*expectedGaps*/65381L,
                        /*expectedHighExpression*/677L,
                        /*expectedScore*/65606L,
                        /*expectedMirrored*/false
                ),
                new TestData(
                        /*emCDM*/"src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        /*lmCDM*/"src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        /*lmGrad*/"src/test/resources/colormipsearch/api/cdsearch/grad/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.png",
                        /*lmZGap*/null,
                        /*expectedGaps*/104449L,
                        /*expectedHighExpression*/16803L,
                        /*expectedScore*/110050L,
                        /*expectedMirrored*/true
                ),
        };
        ImageMaskPredicate excludedRegionsPredicate = ImageTestUtils.getExcludedRegionsPredicate();
        int testQueryThreshold = 20;
        for (TestData td : testData) {
            long start = System.currentTimeMillis();
            ImageArray queryImageArray = ImageReader.readImageArrayFromFile(td.emCDM);

            ColorDepthSearchAlgorithm<ShapeMatchScore> shape2DScoreAlgorithm = new Shape2DMatchColorDepthSearchAlgorithm(
                    queryImageArray,
                    null,
                    testQueryThreshold,
                    true,
                    excludedRegionsPredicate
            );

            ImageArray targetImageArray = ImageReader.readImageArrayFromFile(td.lmCDM);
            ImageArray targetGradImageArray = ImageReader.readImageArrayFromFile(td.lmGrad);

            long endInit = System.currentTimeMillis();
            LOG.info("Initialized shape score between {} and {} in {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    (endInit - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            Map<ComputeFileType, Supplier<ImageArray>> variantSuppliers = new HashMap<ComputeFileType, Supplier<ImageArray>>() {{
                put(ComputeFileType.GradientImage, () -> targetGradImageArray);
                put(ComputeFileType.ZGapImage, () -> {
                    if (td.lmZgap != null) {
                        return ImageReader.readImageArrayFromFile(td.lmZgap);
                    } else {
                        return ImageOperations.rgbMaxFilter2D(
                                ImageOperations.maskRGB(
                                        ImageOperations.maskRegion(targetImageArray, excludedRegionsPredicate),
                                        testQueryThreshold
                                ),
                                10,
                                10
                        );
                    }
                });
            }};
            ShapeMatchScore shapeMatchScore = shape2DScoreAlgorithm.calculateMatchingScore(
                    targetImageArray,
                    variantSuppliers
            );
            long end = System.currentTimeMillis();

            LOG.info("Calculated shape score between {} and {} -> {} ({}, {}, {}) in {} secs, score in {} secs, total {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    shapeMatchScore.getScore(),
                    shapeMatchScore.getGradientAreaGap(),
                    shapeMatchScore.getHighExpressionArea(),
                    shapeMatchScore.isMirrored(),
                    (endInit - start) / 1000.,
                    (end - endInit) / 1000.,
                    (end - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            assertNotNull(td.emCDM + " vs " + td.lmCDM, shapeMatchScore);
            assertTrue(td.emCDM + " vs " + td.lmCDM, shapeMatchScore.getGradientAreaGap() != -1);
            assertTrue(td.emCDM + " vs " + td.lmCDM, shapeMatchScore.getHighExpressionArea() != -1);
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedGaps, shapeMatchScore.getGradientAreaGap());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedHighExpression, shapeMatchScore.getHighExpressionArea());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedScore, shapeMatchScore.getScore());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.mirrored, shapeMatchScore.isMirrored());
        }
    }

}
