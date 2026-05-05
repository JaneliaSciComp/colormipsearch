package org.janelia.colormipsearch.image.io;

import java.io.FileInputStream;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ImageReaderTest {

    private static final Logger LOG = LoggerFactory.getLogger(ImageReaderTest.class);

    @Test
    public void readRGBImageFromFile() throws Exception {
        String[] testFileNames = new String[] {
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack1.tif",
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack2.tif",
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw1.tif",
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw2.tif",
        };
        for (String testFileName : testFileNames) {
            ImageArray imageArray = ImageReader.readImageArrayFromFile(testFileName);
            assertNotNull("Failed to read " + testFileName, imageArray);
            assertTrue("Expected ByteImageArray for " + testFileName, imageArray instanceof RGBByteImageArray);
            assertEquals("Expected 3 channels for RGB image " + testFileName, 3, imageArray.getChannels());
            assertTrue("Width should be > 0 for " + testFileName, imageArray.getWidth() > 0);
            assertTrue("Height should be > 0 for " + testFileName, imageArray.getHeight() > 0);

            // Compare with ImageJ-loaded reference
            ImageArray refArray = TestUtils.readImageArrayWithIJ1(testFileName);

            assertEquals("Width mismatch for " + testFileName, refArray.getWidth(), imageArray.getWidth());
            assertEquals("Height mismatch for " + testFileName, refArray.getHeight(), imageArray.getHeight());

            LOG.info("Read RGB image {}: {}x{} depth={} channels={}",
                    testFileName, imageArray.getWidth(), imageArray.getHeight(),
                    imageArray.getDepth(), imageArray.getChannels());
        }
    }

    @Test
    public void readGrayImageFromFile() throws Exception {
        String[] testFileNames = new String[] {
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack_8b_gray1.tif",
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack_16b_gray1.tif",
        };
        for (String testFileName : testFileNames) {
            ImageArray imageArray = ImageReader.readImageArrayFromFile(testFileName);
            assertNotNull("Failed to read " + testFileName, imageArray);
            assertEquals("Expected 1 channel for grayscale image " + testFileName,
                    1, imageArray.getChannels());
            assertTrue("Width should be > 0 for " + testFileName, imageArray.getWidth() > 0);
            assertTrue("Height should be > 0 for " + testFileName, imageArray.getHeight() > 0);

            LOG.info("Read gray image {}: {}x{} depth={} channels={} type={}",
                    testFileName, imageArray.getWidth(), imageArray.getHeight(),
                    imageArray.getDepth(), imageArray.getChannels(),
                    imageArray.getClass().getSimpleName());
        }
    }

    @Test
    public void readNRRD() {
        String testFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        ImageArray imageArray = ImageReader.readImageArrayFromFile(testFileName);
        assertNotNull(imageArray);
        assertTrue("Expected 3D image (depth > 1)", imageArray.getDepth() > 1);
        assertEquals(1, imageArray.getChannels());
        assertTrue("Width should be > 0", imageArray.getWidth() > 0);
        assertTrue("Height should be > 0", imageArray.getHeight() > 0);

        // Check that the image has some non-zero content
        int nonZeroCount = 0;
        for (int i = 0; i < imageArray.getSpatialSize(); i++) {
            if (imageArray.getPackedIntValAtIndex(i) != 0) {
                nonZeroCount++;
            }
        }
        assertTrue("NRRD should contain non-zero voxels", nonZeroCount > 0);

        LOG.info("Read NRRD {}: {}x{}x{} channels={} type={}, non-zero voxels={}",
                testFileName, imageArray.getWidth(), imageArray.getHeight(),
                imageArray.getDepth(), imageArray.getChannels(),
                imageArray.getClass().getSimpleName(), nonZeroCount);
    }

    @Test
    public void readSWC() {
        class TestData {
            final String fn;
            final int width, height, depth;
            final double xSpacing, ySpacing, zSpacing;

            TestData(String fn, int width, int height, int depth,
                     double xSpacing, double ySpacing, double zSpacing) {
                this.fn = fn;
                this.width = width;
                this.height = height;
                this.depth = depth;
                this.xSpacing = xSpacing;
                this.ySpacing = ySpacing;
                this.zSpacing = zSpacing;
            }
        }

        TestData[] testData = new TestData[] {
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1537331894.swc",
                        685, 283, 87,    // half-res brain dims (matches imglib2 test)
                        1.0378322, 2.0, 1.0
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/27329.swc",
                        287, 560, 110,   // VNC dims (matches imglib2 test)
                        0.922244, 1.4, 1.0
                ),
        };
        for (TestData td : testData) {
            try (FileInputStream fis = new FileInputStream(td.fn)) {
                ImageArray imageArray = SWCImageReader.readSWCStream(
                        fis,
                        td.width, td.height, td.depth,
                        td.xSpacing, td.ySpacing, td.zSpacing,
                        2
                );
                assertNotNull("Failed to read " + td.fn, imageArray);
                assertTrue("Expected ShortImageArray for SWC", imageArray instanceof Gray16ImageArray);
                assertEquals(td.width, imageArray.getWidth());
                assertEquals(td.height, imageArray.getHeight());
                assertEquals(td.depth, imageArray.getDepth());
                assertEquals(1, imageArray.getChannels());

                int nonZeroCount = 0;
                for (int i = 0; i < imageArray.getSpatialSize(); i++) {
                    if (imageArray.getPackedIntValAtIndex(i) != 0) {
                        nonZeroCount++;
                    }
                }
                LOG.info("Read SWC {}: {}x{}x{} channels={}, non-zero voxels={}",
                        td.fn, imageArray.getWidth(), imageArray.getHeight(),
                        imageArray.getDepth(), imageArray.getChannels(), nonZeroCount);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read " + td.fn, e);
            }
        }
    }

    @Test
    public void readImageFromStream() throws Exception {
        String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack1.tif";

        ImageArray fromFile = ImageReader.readImageArrayFromFile(testFileName);
        ImageArray fromStream;
        try (FileInputStream fis = new FileInputStream(testFileName)) {
            fromStream = ImageReader.readImageArrayFromStream(testFileName, fis);
        }

        assertNotNull(fromFile);
        assertNotNull(fromStream);
        assertEquals(fromFile.getWidth(), fromStream.getWidth());
        assertEquals(fromFile.getHeight(), fromStream.getHeight());
        assertEquals(fromFile.getDepth(), fromStream.getDepth());
        assertEquals(fromFile.getChannels(), fromStream.getChannels());

        // Verify pixel data matches
        for (int i = 0; i < fromFile.getSpatialSize(); i++) {
            assertEquals("Pixel mismatch at index " + i, fromFile.getPackedIntValAtIndex(i), fromStream.getPackedIntValAtIndex(i));
        }

        LOG.info("Verified readImageArrayFromFile and readImageArrayFromStream produce identical results for {}",
                testFileName);
    }
}
