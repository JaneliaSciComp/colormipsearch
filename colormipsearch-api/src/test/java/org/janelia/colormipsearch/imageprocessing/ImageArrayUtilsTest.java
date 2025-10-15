package org.janelia.colormipsearch.imageprocessing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;
import org.junit.Assert;
import org.junit.Test;

public class ImageArrayUtilsTest {

    @Test
    public void readImageRangeForPackBits() throws Exception {
        for (int i = 0; i < 2; i++) {
            File testFile = new File("src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif");
            ImagePlus testImage = new Opener().openTiff(testFile.getAbsolutePath(), 1);
            int[] testImageBoundaries = getImageBoundaries(testImage.getProcessor());
            ImageArray<?> testImageArray = ImageArrayUtils.readImageArrayRange(
                    "test",
                    testFile.getName(),
                    openFile(testFile),
                    testImageBoundaries[1] * testImage.getProcessor().getWidth(),
                    testImageBoundaries[3] * testImage.getProcessor().getWidth() + testImageBoundaries[2]);
            ImageProcessor ip = testImage.getProcessor();
            for (int y = 0; y < ip.getHeight(); y++) {
                if (y >= testImageBoundaries[1] && y <= testImageBoundaries[3]) {
                    for (int x = 0; x < ip.getWidth(); x++) {
                        Assert.assertEquals(ip.getPixel(x, y), testImageArray.getPixel(x, y));
                    }
                } else {
                    for (int x = 0; x < ip.getWidth(); x++) {
                        Assert.assertEquals(0xFF000000, testImageArray.getPixel(x, y));
                    }
                }
            }
        }
    }

    @Test
    public void readImageRangeForOtherCompression() throws Exception {
        for (int i = 0; i < 2; i++) {
            File testFile = new File("src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw" + (i % 2 + 1) + ".tif");
            ImagePlus testImage = new Opener().openTiff(testFile.getAbsolutePath(), 1);
            int[] testImageBoundaries = getImageBoundaries(testImage.getProcessor());
            ImageArray<?> testImageArray = ImageArrayUtils.readImageArrayRange(
                    "test",
                    testFile.getName(),
                    openFile(testFile),
                    (long) testImageBoundaries[1] * testImage.getProcessor().getWidth(),
                    (long) testImageBoundaries[3] * testImage.getProcessor().getWidth() + testImageBoundaries[2]);
            ImageProcessor ip = testImage.getProcessor();
            for (int y = 0; y < ip.getHeight(); y++) {
                for (int x = 0; x < ip.getWidth(); x++) {
                    Assert.assertEquals(ip.getPixel(x, y), testImageArray.getPixel(x, y));
                }
            }
        }
    }

    private int[] getImageBoundaries(ImageProcessor ip) {
        int minx = ip.getWidth();
        int miny = ip.getHeight();
        int maxx = 0;
        int maxy = 0;
        for (int pi = 0; pi < ip.getPixelCount(); pi++) {
            int p = ip.get(pi);
            if ((p & 0xFFFFFF) != 0) {
                int x = pi % ip.getWidth();
                int y = pi / ip.getWidth();
                if (x < minx) minx = x;
                if (y < miny) miny = y;
                if (x + 1 > maxx) maxx = x + 1;
                if (y + 1 > maxy) maxy = y + 1;
            }
        }
        return new int[]{
                minx, (miny + (maxy - miny) / 6),
                maxx, (maxy - (maxy - miny) / 6)
        };
    }

    private InputStream openFile(File f) {
        try {
            return Files.newInputStream(f.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
