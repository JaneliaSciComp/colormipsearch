package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Collections;

import ij.ImagePlus;
import ij.io.Opener;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PixelMatchColorDepthSearchAlgorithmTest {

    private static final int CDMIP_WIDTH = 1210;
    private static final int CDMIP_HEIGHT = 566;

    @Test
    public void cdsMatchWithDirectBestMatch() {
        Img<ByteArrayRGBPixelType> mask = createTestMaskImage();
        Img<ByteArrayRGBPixelType> target = createTestTargetImage(false);
        long startTime = System.currentTimeMillis();
        PixelMatchColorDepthSearchAlgorithm cdsAlg = new PixelMatchColorDepthSearchAlgorithm(
                mask,
                null,
                100,
                100,
                true,
                2,
                2);
        PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(target, Collections.emptyMap());
        long endTime = System.currentTimeMillis();
        System.out.printf("Completed CDS in %f secs\n", (endTime-startTime)/1000.);
        assertEquals(20000, cdsScore.getScore());
        assertFalse(cdsScore.isMirrored());
    }

    @Test
    public void cdsMatchWithMirroredBestMatch() {
        Img<ByteArrayRGBPixelType> mask = createTestMaskImage();
        Img<ByteArrayRGBPixelType> target = createTestTargetImage(true);
        long startTime = System.currentTimeMillis();
        PixelMatchColorDepthSearchAlgorithm cdsAlg = new PixelMatchColorDepthSearchAlgorithm(
                mask,
                null,
                100,
                100, true,
                2,
                2);
        PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(target, Collections.emptyMap());
        long endTime = System.currentTimeMillis();
        System.out.printf("Completed CDS in %f secs\n", (endTime-startTime)/1000.);
        assertEquals(20000, cdsScore.getScore());
        assertTrue(cdsScore.isMirrored());
    }

    private Img<ByteArrayRGBPixelType> createTestMaskImage() {
        final Img<UnsignedByteType> img = new ArrayImgFactory<>(new UnsignedByteType())
                .create(CDMIP_WIDTH, CDMIP_HEIGHT, 3);
        for (int y = 300; y < 400; y++) {
            for (int x = 100; x < 200; x++) {
                img.randomAccess().setPositionAndGet(x, y, 0).set(254);
                img.randomAccess().setPositionAndGet(x, y, 1).set(255);
                img.randomAccess().setPositionAndGet(x, y, 2).set(0);
            }
            for (int x = 200; x < 300; x++) {
                img.randomAccess().setPositionAndGet(x, y, 0).set(54);
                img.randomAccess().setPositionAndGet(x, y, 1).set(255);
                img.randomAccess().setPositionAndGet(x, y, 2).set(201);
            }
        }
        return ImageAccessUtils.createRGBImageFromMultichannelImg(img, new ByteArrayRGBPixelType());
    }

    private Img<ByteArrayRGBPixelType> createTestTargetImage(boolean mirror) {
        final Img<UnsignedByteType> img = new ArrayImgFactory<>(new UnsignedByteType())
                .create(CDMIP_WIDTH, CDMIP_HEIGHT, 3);

        for (int y = 300; y < 400; y++) {
            for (int x = 102; x < 202; x++) {
                int xToUse = mirror ? CDMIP_WIDTH - x - 1 : x;
                img.randomAccess().setPositionAndGet(xToUse, y, 0).set(254);
                img.randomAccess().setPositionAndGet(xToUse, y, 1).set(255);
                img.randomAccess().setPositionAndGet(xToUse, y, 2).set(0);
            }
            for (int x = 202; x < 302; x++) {
                int xToUse = mirror ? CDMIP_WIDTH - x - 1 : x;
                img.randomAccess().setPositionAndGet(xToUse, y, 0).set(54);
                img.randomAccess().setPositionAndGet(xToUse, y, 1).set(255);
                img.randomAccess().setPositionAndGet(xToUse, y, 2).set(201);
            }
        }
        return ImageAccessUtils.createRGBImageFromMultichannelImg(img, new ByteArrayRGBPixelType());
    }

    @Test
    public void pixelMatchScore() {
        String emFilename = "src/test/resources/colormipsearch/api/cdsearch/1752016801-LPLC2-RT_18U.tif";
        class TestData {
                final String lmFilename;
                final int expectedScore;
                final boolean bestIsMirrored;

            TestData(String lmFilename, int expectedScore, boolean bestIsMirrored) {
                this.lmFilename = lmFilename;
                this.expectedScore = expectedScore;
                this.bestIsMirrored = bestIsMirrored;
            }
        };
        TestData[] testData = new TestData[] {
                new TestData("src/test/resources/colormipsearch/api/cdsearch/GMR_31G04_AE_01-20190813_66_F3-40x-Brain-JRC2018_Unisex_20x_HR-2704505419467849826-CH2-07_CDM.tif",
                        87,
                        false),
                new TestData("src/test/resources/colormipsearch/api/cdsearch/0342_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02.png",
                        0,
                        false),
                new TestData(emFilename,
                        1897,
                        false),
        };
        Img<ByteArrayRGBPixelType> testMask = ImageReader.readRGBImage(emFilename, new ByteArrayRGBPixelType());

        long startInitTime = System.currentTimeMillis();
        PixelMatchColorDepthSearchAlgorithm cdsAlg = new PixelMatchColorDepthSearchAlgorithm(
                testMask,
                (pos, p) -> pos[0] >= CDMIP_WIDTH - 260 && pos[1] < 90 || pos[0] < 330 && pos[1] < 100,
                20,
                20,
                true,
                0.01,
                2);
        long endInitTime = System.currentTimeMillis();
        System.out.printf("Completed CDS init for %s: %f secs\n",
                emFilename,
                (endInitTime-startInitTime)/1000.);
        for (TestData td : testData) {
            Img<ByteArrayRGBPixelType> testTarget = ImageReader.readRGBImage(td.lmFilename, new ByteArrayRGBPixelType());
            long startComputeTime = System.currentTimeMillis();
            PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(testTarget, Collections.emptyMap());
            long endComputeTime = System.currentTimeMillis();
            System.out.printf("Completed CDS for %s: %f secs; score=%d\n",
                    td.lmFilename,
                    (endComputeTime-startComputeTime)/1000.,
                    cdsScore.getScore());
            assertEquals(td.expectedScore, cdsScore.getScore());
            assertEquals(td.bestIsMirrored, cdsScore.isMirrored());
        }
    }

    @Test
    public void pixelMatchScoreForOpticLobe() {
        String emFilename = "src/test/resources/colormipsearch/api/cdsearch/125553_jrc18U_um.tif";
        class TestData {
            final String lmFilename;
            final int expectedScore;
            final boolean bestIsMirrored;

            TestData(String lmFilename, int expectedScore, boolean bestIsMirrored) {
                this.lmFilename = lmFilename;
                this.expectedScore = expectedScore;
                this.bestIsMirrored = bestIsMirrored;
            }
        };
        TestData[] testData = new TestData[] {
                new TestData("src/test/resources/colormipsearch/api/cdsearch/BJD_100E04_AE_01-20170929_63_C1-40x-Brain-JRC2018_Unisex_20x_HR-2462451865904742498-CH1-01_CDM.tif",
                        0,
                        false),
                new TestData("src/test/resources/colormipsearch/api/cdsearch/BJD_100E04_AE_01-20170929_63_C5-40x-Brain-JRC2018_Unisex_20x_HR-2462451866454196322-CH2-02_CDM.tif",
                        16,
                        false),
        };
        Img<ByteArrayRGBPixelType> testMask = ImageReader.readRGBImage(emFilename, new ByteArrayRGBPixelType());

        long startInitTime = System.currentTimeMillis();
        PixelMatchColorDepthSearchAlgorithm cdsAlg = new PixelMatchColorDepthSearchAlgorithm(
                testMask,
                (pos, p) -> pos[0] >= CDMIP_WIDTH - 260 && pos[1] < 90 || pos[0] < 330 && pos[1] < 100,
                20,
                20,
                true,
                0.01,
                2);
        long endInitTime = System.currentTimeMillis();
        System.out.printf("Completed CDS init for %s: %f secs\n",
                emFilename,
                (endInitTime-startInitTime)/1000.);
        for (TestData td : testData) {
            Img<ByteArrayRGBPixelType> testTarget = ImageReader.readRGBImage(td.lmFilename, new ByteArrayRGBPixelType());
            long startComputeTime = System.currentTimeMillis();
            PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(testTarget, Collections.emptyMap());
            long endComputeTime = System.currentTimeMillis();
            System.out.printf("Completed CDS for %s: %f secs; score=%d\n",
                    td.lmFilename,
                    (endComputeTime-startComputeTime)/1000.,
                    cdsScore.getScore());
            assertEquals(td.expectedScore, cdsScore.getScore());
            assertEquals(td.bestIsMirrored, cdsScore.isMirrored());
        }
    }

}
