package org.janelia.colormipsearch.image.algorithms;

import java.util.function.BiPredicate;

import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.filter.RankFilters;
import ij.process.ImageProcessor;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class MaxFilterAlgorithmTest {

    private static final Logger LOG = LoggerFactory.getLogger(MaxFilterAlgorithmTest.class);

    @Test
    public void maxFilterForRGBImage() {
        final int radius = 10;
        RankFilters rankFilters = new RankFilters();

        for (int i = 1; i < 5; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

            long startTime = System.currentTimeMillis();
            ImageArray maxFilteredImage = MaxFilterAlgorithm.rgbMaxFilter2D(testMIP, radius);
            long endMaxFilterTime = System.currentTimeMillis();

            // IJ1 reference
            ImageProcessor refImageProcessor = TestUtils.sliceToIJ1Processor(testMIP, 0, testMIP.getWidth(), testMIP.getHeight(), testMIP.getChannels());
            rankFilters.rank(refImageProcessor, radius, RankFilters.MAX);
            long endIJ1MaxFilterTime = System.currentTimeMillis();

            int ndiffs = 0;
            for (int r = 0; r < testMIP.getHeight(); r++) {
                for (int c = 0; c < testMIP.getWidth(); c++) {
                    int j = r * testMIP.getWidth() + c;
                    if ((maxFilteredImage.get(j) & 0x00FFFFFF) != (refImageProcessor.get(j) & 0x00FFFFFF)) {
                        ndiffs++;
                    }
                }
            }
            LOG.info("MaxFilter time {} vs {} - IJ1 maxFilter time - found {} diffs",
                    (endMaxFilterTime - startTime) / 1000.,
                    (endIJ1MaxFilterTime - endMaxFilterTime) / 1000.,
                    ndiffs);
            Assert.assertEquals(testImageName, 0, ndiffs);
        }
    }

    @Test
    public void maxFilterThenHorizontalMirroringForRGBImage() {
        final int radius = 10;

        for (int i = 1; i < 6; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

            ImageArray maxFilteredImage = MaxFilterAlgorithm.rgbMaxFilter2D(testMIP, radius);
            ImageArray result = mirrorHorizontal(maxFilteredImage);

            // IJ1 reference: max filter then flip
            ImagePlus refImage = new Opener().openTiff(testImageName, 1);
            new RankFilters().rank(refImage.getProcessor(), radius, RankFilters.MAX);
            refImage.getProcessor().flipHorizontal();

            for (int j = 0; j < result.getPixelCount(); j++) {
                Assert.assertEquals(
                        refImage.getProcessor().get(j) & 0x00FFFFFF,
                        result.get(j) & 0x00FFFFFF);
            }
        }
    }

    @Test
    public void horizontalMirrorThenMaxFilterForRGBImage() {
        final int radius = 10;

        for (int i = 0; i < 5; i++) {
            String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

            ImageArray mirrored = mirrorHorizontal(testMIP);
            ImageArray result = MaxFilterAlgorithm.rgbMaxFilter2D(mirrored, radius);

            // IJ1 reference: flip then max filter
            ImagePlus refImage = new Opener().openTiff(testImageName, 1);
            refImage.getProcessor().flipHorizontal();
            new RankFilters().rank(refImage.getProcessor(), radius, RankFilters.MAX);

            for (int j = 0; j < result.getPixelCount(); j++) {
                Assert.assertEquals(
                        refImage.getProcessor().get(j) & 0x00FFFFFF,
                        result.get(j) & 0x00FFFFFF);
            }
        }
    }

    @Test
    public void maxFilterForGrayImage() {
        final int radius = 10;

        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif";
        ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

        // Convert to gray for our code
        ImageArray grayImage = toGray8(testMIP);
        ImageArray maxFilteredImage = MaxFilterAlgorithm.maxFilterGray(grayImage, radius, radius, 0);

        // IJ1 reference
        ImagePlus refImage = new Opener().openTiff(testImageName, 1);
        ij.process.ImageProcessor asByteProcessor = refImage.getProcessor().convertToByte(true);
        new RankFilters().rank(asByteProcessor, radius, RankFilters.MAX);

        for (int i = 0; i < asByteProcessor.getPixelCount(); i++) {
            Assert.assertEquals(asByteProcessor.get(i), maxFilteredImage.get(i));
        }
    }

    @Test
    public void overExpressesMaskExpression() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/1281324958-DNp11-RT_18U_FL.tif";
        ImageArray testImageArray = ImageReader.readImageArrayFromFile(testImageName);

        // Clear label region
        BiPredicate<Integer, Integer> labelRegion = (x, y) ->
                x < 330 && y < 100 || x >= testImageArray.getWidth() - 250 && y < 90;
        ImageArray clearedImage = clearRegion(testImageArray, labelRegion);

        // Max filter at two radii
        ImageArray mask60 = MaxFilterAlgorithm.rgbMaxFilter2D(clearedImage, 60);
        ImageArray mask20 = MaxFilterAlgorithm.rgbMaxFilter2D(clearedImage, 20);

        // Subtract: keep pixels from the 60x image that are NOT in the 20x image
        ImageArray diff = combine(mask60, mask20,
                (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1);

        // Convert to gray16 signal
        ImageArray gray = toGray16(diff);
        ImageArray signal = toBinarySignal(gray, 0);

        // Count non-zero pixels
        int nonZeroPxs = 0;
        for (int i = 0; i < signal.getPixelCount(); i++) {
            if (signal.get(i) != 0) nonZeroPxs++;
        }
        assertTrue(nonZeroPxs > 0);
    }

    @Test
    public void maskThenMaxFilterRGB() {
        String testImageName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif";
        ImageArray testMIP = ImageReader.readImageArrayFromFile(testImageName);

        // Apply threshold mask then max filter
        ImageArray masked = applyThresholdMask(testMIP, 250);
        ImageArray result = MaxFilterAlgorithm.rgbMaxFilter2D(masked, 10);

        Assert.assertNotNull(result);
        Assert.assertEquals(testMIP.getWidth(), result.getWidth());
        Assert.assertEquals(testMIP.getHeight(), result.getHeight());
    }

    // ---- helper methods (local until plan Phase 1 creates the algorithm classes) ----

    private static ImageArray mirrorHorizontal(ImageArray input) {
        ImageArray output = input.getFactory().create(
                input.getWidth(), input.getHeight(), input.getDepth(), input.getChannels());
        int w = input.getWidth();
        int h = input.getHeight();
        int d = input.getDepth();
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    output.setIntPixel(x, y, z, input.getIntPixel(w - 1 - x, y, z));
                }
            }
        }
        return output;
    }

    private static ImageArray clearRegion(ImageArray input, BiPredicate<Integer, Integer> region) {
        ImageArray output = input.getFactory().create(
                input.getWidth(), input.getHeight(), input.getDepth(), input.getChannels());
        int w = input.getWidth();
        int h = input.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (region.test(x, y)) {
                    output.setIntPixel(x, y, 0, 0xFF000000);
                } else {
                    output.setIntPixel(x, y, 0, input.getIntPixel(x, y, 0));
                }
            }
        }
        return output;
    }

    private static ImageArray combine(ImageArray a, ImageArray b,
                                      java.util.function.IntBinaryOperator op) {
        ImageArray output = a.getFactory().create(
                a.getWidth(), a.getHeight(), a.getDepth(), a.getChannels());
        for (int i = 0; i < a.getPixelCount(); i++) {
            output.set(i, op.applyAsInt(a.get(i), b.get(i)));
        }
        return output;
    }

    private static ImageArray toGray8(ImageArray rgb) {
        org.janelia.colormipsearch.image.ByteImageArray output =
                new org.janelia.colormipsearch.image.ByteImageArray(
                        rgb.getWidth(), rgb.getHeight(), rgb.getDepth(), 1);
        for (int i = 0; i < rgb.getPixelCount(); i++) {
            int p = rgb.get(i);
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            output.set(i, (r + g + b) / 3);
        }
        return output;
    }

    private static ImageArray toGray16(ImageArray input) {
        org.janelia.colormipsearch.image.ShortImageArray output =
                new org.janelia.colormipsearch.image.ShortImageArray(
                        input.getWidth(), input.getHeight(), input.getDepth(), 1);
        for (int i = 0; i < input.getPixelCount(); i++) {
            int p = input.get(i);
            if (input.getChannels() >= 3) {
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                output.set(i, (r + g + b) / 3);
            } else {
                output.set(i, p);
            }
        }
        return output;
    }

    private static ImageArray toBinarySignal(ImageArray input, int threshold) {
        org.janelia.colormipsearch.image.ByteImageArray output =
                new org.janelia.colormipsearch.image.ByteImageArray(
                        input.getWidth(), input.getHeight(), input.getDepth(), 1);
        for (int i = 0; i < input.getPixelCount(); i++) {
            output.set(i, input.get(i) > threshold ? 1 : 0);
        }
        return output;
    }

    private static ImageArray applyThresholdMask(ImageArray input, int threshold) {
        ImageArray output = input.getFactory().create(
                input.getWidth(), input.getHeight(), input.getDepth(), input.getChannels());
        for (int i = 0; i < input.getPixelCount(); i++) {
            int p = input.get(i);
            if (input.getChannels() >= 3) {
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r <= threshold && g <= threshold && b <= threshold) {
                    output.set(i, 0xFF000000);
                } else {
                    output.set(i, p);
                }
            } else {
                output.set(i, p <= threshold ? 0 : p);
            }
        }
        return output;
    }
}