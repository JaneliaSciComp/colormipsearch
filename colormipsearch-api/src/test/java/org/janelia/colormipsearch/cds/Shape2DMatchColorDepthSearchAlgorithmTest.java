package org.janelia.colormipsearch.cds;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import ij.ImagePlus;
import ij.io.Opener;
import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.imageprocessing.ColorTransformation;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageArrayUtils;
import org.janelia.colormipsearch.imageprocessing.ImageProcessing;
import org.janelia.colormipsearch.imageprocessing.ImageRegionDefinition;
import org.janelia.colormipsearch.imageprocessing.ImageTransformation;
import org.janelia.colormipsearch.imageprocessing.LImage;
import org.janelia.colormipsearch.imageprocessing.LImageUtils;
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
        long startTime = System.currentTimeMillis();
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif";

        ImagePlus emQueryImage = new Opener().openTiff(emCDM, 1);
        ImageArray<?> queryImageArray = ImageArrayUtils.fromImagePlus(emQueryImage);

        ImageRegionDefinition excludedRegions = ImageTestUtils.getExcludedRegions();
        ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(excludedRegions.getRegion(queryImageArray));
        LImage queryImage = LImageUtils.create(queryImageArray, 0, 0, 0, 0).mapi(clearIgnoredRegions);

        LImage maskForRegionsWithTooMuchExpression = LImageUtils.combine2(
                queryImage.mapi(ImageTransformation.unsafeMaxFilter(60)),
                queryImage.mapi(ImageTransformation.unsafeMaxFilter(20)),
                (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1 // mask pixels from the 60x image if they are present in the 20x image
        ).map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(0)).reduce();

        LImage queryMask = queryImage.map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(2)).reduce();

        long sizeMask1 = queryMask.fold(0L, Long::sum);
        long sizeHighExpressions1 = maskForRegionsWithTooMuchExpression.fold(0L, Long::sum);
        assertEquals(17340, sizeMask1);
        assertEquals(70640, sizeHighExpressions1);
        long sizeMask2 = queryMask.fold(0L, Long::sum); // I want to check that nothing changes on the second traversal
        long sizeHighExpressions2 = maskForRegionsWithTooMuchExpression.fold(0L, Long::sum);
        assertEquals(sizeMask1, sizeMask2);
        assertEquals(sizeHighExpressions1, sizeHighExpressions2);
        LOG.info("Computed size of high expression area ({}) and mask size ({}) in {}sec", sizeHighExpressions1, sizeMask1, System.currentTimeMillis() - startTime);
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

        TestData[] testData = new TestData[] {
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
        ImageRegionDefinition excludedRegions = ImageTestUtils.getExcludedRegions();
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore> shapeScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                true,
                null,
                excludedRegions
        );
        int testQueryThreshold = 20;
        String prevEM = null;
        ColorDepthSearchAlgorithm<ShapeMatchScore> shape2DScoreAlgorithm = null;
        for (TestData td : testData) {
            long start = System.currentTimeMillis();
            if (!td.emCDM.equals(prevEM)) {
                LOG.info("Create new score algorithm for new mask: {}", td.emCDM);
                ImagePlus emQueryImage = new Opener().openTiff(td.emCDM, 1);
                ImageArray<?> queryImageArray = ImageArrayUtils.fromImagePlus(emQueryImage);
                shape2DScoreAlgorithm = shapeScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        queryImageArray,
                        testQueryThreshold,
                        0
                );
                prevEM = td.emCDM;
            }
            ImagePlus lmTargetImage = new Opener().openTiff(td.lmCDM, 1);
            ImagePlus lmTargetGradImage = new Opener().openImage(td.lmGrad);

            ImageArray<?> targetImageArray = ImageArrayUtils.fromImagePlus(lmTargetImage);
            ImageArray<?> targetGradImageArray = ImageArrayUtils.fromImagePlus(lmTargetGradImage);
            ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(excludedRegions.getRegion(targetImageArray));

            long endInit = System.currentTimeMillis();
            LOG.info("Initialized shape score between {} and {} in {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    (endInit - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            Map<ComputeFileType, Supplier<ImageArray<?>>> variantSuppliers = new HashMap<ComputeFileType, Supplier<ImageArray<?>>>() {{
                put(ComputeFileType.GradientImage, () -> targetGradImageArray);
                put(ComputeFileType.ZGapImage, () -> ImageProcessing.create(clearIgnoredRegions)
                        .applyColorTransformation(ColorTransformation.mask(testQueryThreshold))
                        .unsafeMaxFilter(10)
                        .applyTo(LImageUtils.create(targetImageArray)).toImageArray());
            }};
            ShapeMatchScore shapeMatchScore =  shape2DScoreAlgorithm.calculateMatchingScore(
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

        TestData[] testData = new TestData[] {
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
        ImageRegionDefinition excludedRegions = ImageTestUtils.getExcludedRegions();
        int testQueryThreshold = 20;
        for (TestData td : testData) {
            long start = System.currentTimeMillis();
            ImagePlus emQueryImage = new Opener().openTiff(td.emCDM, 1);
            ImageArray<?> queryImageArray = ImageArrayUtils.fromImagePlus(emQueryImage);
            ImageTransformation clearIgnoredRegions = ImageTransformation.clearRegion(excludedRegions.getRegion(queryImageArray));
            LImage queryImage = LImageUtils.create(queryImageArray, 0, 0, 0, 0).mapi(clearIgnoredRegions);
            LImage maskForRegionsWithTooMuchExpression = LImageUtils.combine2(
                    queryImage.mapi(ImageTransformation.unsafeMaxFilter(60)),
                    queryImage.mapi(ImageTransformation.unsafeMaxFilter(20)),
                    (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1 // mask pixels from the 60x image if they are present in the 20x image
            ).map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(0)).reduce();

            LImage queryMask = queryImage.map(ColorTransformation.toGray16WithNoGammaCorrection()).map(ColorTransformation.gray8Or16ToSignal(2)).reduce();

            ColorDepthSearchAlgorithm<ShapeMatchScore> shape2DScoreAlgorithm = new Shape2DMatchColorDepthSearchAlgorithm(
                    queryImage,
                    queryMask,
                    maskForRegionsWithTooMuchExpression,
                    null,
                    testQueryThreshold,
                    true,
                    clearIgnoredRegions
            );

            ImagePlus lmTargetImage = new Opener().openTiff(td.lmCDM, 1);
            ImagePlus lmTargetGradImage = new Opener().openImage(td.lmGrad);

            ImageArray<?> targetImageArray = ImageArrayUtils.fromImagePlus(lmTargetImage);
            ImageArray<?> targetGradImageArray = ImageArrayUtils.fromImagePlus(lmTargetGradImage);

            long endInit = System.currentTimeMillis();
            LOG.info("Initialized shape score between {} and {} in {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    (endInit - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            Map<ComputeFileType, Supplier<ImageArray<?>>> variantSuppliers = new HashMap<ComputeFileType, Supplier<ImageArray<?>>>() {{
                put(ComputeFileType.GradientImage, () -> targetGradImageArray);
                put(ComputeFileType.ZGapImage, () -> {
                    if (td.lmZgap != null) {
                        ImagePlus zgapImage = new Opener().openTiff(td.lmZgap, 1);
                        return ImageArrayUtils.fromImagePlus(zgapImage);
                    } else {
                        return ImageProcessing.create(clearIgnoredRegions)
                                .applyColorTransformation(ColorTransformation.mask(testQueryThreshold))
                                .unsafeMaxFilter(10)
                                .applyTo(LImageUtils.create(targetImageArray)).toImageArray();
                    }
                });
            }};
            ShapeMatchScore shapeMatchScore =  shape2DScoreAlgorithm.calculateMatchingScore(
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
