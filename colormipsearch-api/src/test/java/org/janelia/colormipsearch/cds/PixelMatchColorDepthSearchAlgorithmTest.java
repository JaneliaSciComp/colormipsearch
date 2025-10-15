package org.janelia.colormipsearch.cds;

import java.util.Collections;
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
import org.janelia.colormipsearch.imageprocessing.LImageUtils;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PixelMatchColorDepthSearchAlgorithmTest {

    private static final Logger LOG = LoggerFactory.getLogger(PixelMatchColorDepthSearchAlgorithmTest.class);

    @Test
    public void pixelMatchScore() {
        ImagePlus testMask = new Opener().openTiff("src/test/resources/colormipsearch/api/cdsearch/ems/1752016801-LPLC2-RT_18U.tif", 1);
        ImagePlus testTarget = new Opener().openTiff("src/test/resources/colormipsearch/api/cdsearch/lms/GMR_31G04_AE_01-20190813_66_F3-40x-Brain-JRC2018_Unisex_20x_HR-2704505419467849826-CH2-07_CDM.tif", 1);
        ImageArray<?> testMaskArray = ImageArrayUtils.fromImagePlus(testMask);
        ImageArray<?> testTargetArray = ImageArrayUtils.fromImagePlus(testTarget);
        PixelMatchColorDepthSearchAlgorithm colorDepthSearchAlgorithm = new PixelMatchColorDepthSearchAlgorithm(
            testMaskArray,
            20,
            true,
            null,
            0,
            false,
            20,
            0.01,
            2,
            img -> (x, y) -> x >= img.getWidth() - 260 && y < 90 || x < 330 && y < 100
        );
        PixelMatchScore score = colorDepthSearchAlgorithm.calculateMatchingScore(testTargetArray, Collections.emptyMap());
        assertEquals(87, score.getScore());
        assertFalse(score.isMirrored());
    }

    @Test
    public void multiplePixelScores() {

        class TestData {
            final String emCDM;
            final String lmCDM;
            final long expectedScore;
            final boolean mirrored; // if true the score comes from the mirrored mask

            TestData(String emCDM, String lmCDM, long expectedScore, boolean mirrored) {
                this.emCDM = emCDM;
                this.lmCDM = lmCDM;
                this.expectedScore = expectedScore;
                this.mirrored = mirrored;
            }
        }

        TestData[] testData = new TestData[] {
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        439,
                        false
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif",
                        515,
                        false
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U_FL.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        483,
                        false
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif",
                        "src/test/resources/colormipsearch/api/cdsearch/lms/VT016795_115C08_AE_01-20200221_61_I2-m-CH1_01.tif",
                        426,
                        true
                ),
        };
        for (TestData td : testData) {
            long start = System.currentTimeMillis();
            ImagePlus emQueryImage = new Opener().openTiff(td.emCDM, 1);
            ImagePlus lmTargetImage = new Opener().openTiff(td.lmCDM, 1);

            ImageArray<?> queryImageArray = ImageArrayUtils.fromImagePlus(emQueryImage);
            ImageArray<?> targetImageArray = ImageArrayUtils.fromImagePlus(lmTargetImage);

            int testThreshold = 20;
            ColorDepthSearchAlgorithmProvider<PixelMatchScore> pixelScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createPixMatchCDSAlgorithmProvider(
                    true,
                    testThreshold,
                    1,
                    2,
                    ImageTestUtils.getExcludedRegions()
            );
            ColorDepthSearchAlgorithm<PixelMatchScore> pixelScoreAlgorithm = pixelScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                    queryImageArray,
                    testThreshold,
                    0
            );
            long endInit = System.currentTimeMillis();
            LOG.info("Initialized pixel score between {} and {} in {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    (endInit - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));

            PixelMatchScore pixelMatchScore =  pixelScoreAlgorithm.calculateMatchingScore(
                    targetImageArray,
                    Collections.emptyMap()
            );

            long end = System.currentTimeMillis();
            assertNotNull(td.emCDM + " vs " + td.lmCDM, pixelMatchScore);
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.expectedScore, pixelMatchScore.getScore());
            assertEquals(td.emCDM + " vs " + td.lmCDM, td.mirrored, pixelMatchScore.isMirrored());

            LOG.info("Calculated shape score between {} and {} -> {} ({}, {}) in {} secs, score in {} secs, total {} secs - mem used {}M",
                    td.emCDM,
                    td.lmCDM,
                    pixelMatchScore.getScore(),
                    pixelMatchScore.getNormalizedScore(),
                    pixelMatchScore.isMirrored(),
                    (endInit - start) / 1000.,
                    (end - endInit) / 1000.,
                    (end - start) / 1000.,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024. * 1024 * 1024));
        }
    }

}
