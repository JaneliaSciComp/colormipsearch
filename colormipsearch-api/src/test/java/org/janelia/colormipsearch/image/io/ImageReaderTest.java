package org.janelia.colormipsearch.image.io;

import java.io.IOException;

import io.scif.img.ImgOpener;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.junit.Assert;
import org.junit.Test;
import org.scijava.io.location.FileLocation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ImageReaderTest {

    @Test
    public void readImageRangeWithPackBitsCompression() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            RandomAccessibleInterval<ARGBType> refImage = readRGBImage(testFileName);
            assertEquals(2, refImage.numDimensions());

            ImageAccess<? extends RGBPixelType<?>> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            compareRGBImages(refImage, testImage);
        }
    }

    @Test
    public void readGrayImage() {
        String[] testFileNames = new String[] {
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack_8b_gray1.tif",
                "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack_16b_gray1.tif",
        };
        for (String testFileName : testFileNames) {
            RandomAccessibleInterval<UnsignedIntType> refImage = readGrayImage(testFileName, new UnsignedIntType());
            assertEquals(2, refImage.numDimensions());

            ImageAccess<UnsignedIntType> testImage = ImageReader.readImage(testFileName, new UnsignedIntType());

            compareGrayImages(refImage, testImage);
        }
    }

    @Test
    public void readRGBArrayImageWithPackBitsCompression() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            RandomAccessibleInterval<ARGBType> refImage = readRGBImage(testFileName);
            assertEquals(2, refImage.numDimensions());

            ImageAccess<? extends RGBPixelType<?>> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            compareRGBImages(refImage, testImage);
        }
    }

    @Test
    public void readNRRD() {
        String testFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        ImageAccess<UnsignedIntType> testImage = ImageReader.readImage(testFileName, new UnsignedIntType(0));
        assertEquals(3, testImage.numDimensions());
        TestUtils.displayNumericImage(testImage);
    }

    @Test
    public void readSWC() {
        class TestData {
            String fn;
            final int[] dims;
            final double[] scaling;
            final UnsignedIntType swcPixel;

            TestData(String fn, int[] dims, double[] scaling, UnsignedIntType swcPixel) {
                this.fn = fn;
                this.dims = dims;
                this.scaling = scaling;
                this.swcPixel = swcPixel;
            }
        }
        TestData[] testData = new TestData[] {
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1537331894.swc",
                        new int[] {685, 283, 87},
                        new double[] {1.0378322, 2.0, 1},
                        new UnsignedIntType(0)), // brain dims
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/27329.swc",
                        new int[] {287, 560, 110},
                        new double[] {0.922244, 1.4, 1},
                        new UnsignedIntType(255)), // vnc dims
        };
        for (TestData td : testData) {
            ImageAccess<UnsignedIntType> testImage =
                    ImageReader.readSWC(td.fn,
                            td.dims[0], td.dims[1],td.dims[2],
                            td.scaling[0], td.scaling[1],td.scaling[2],
                            td.swcPixel);
            assertEquals(3, testImage.numDimensions());
            TestUtils.displayNumericImage(testImage);
        }
    }

    private RandomAccessibleInterval<ARGBType> readRGBImage(String fn) {
        Img<UnsignedByteType> image = new ImgOpener().openImgs(new FileLocation(fn), new UnsignedByteType(0)).get(0);
        return Converters.mergeARGB(image, ColorChannelOrder.RGB);
    }

    private <T extends NativeType<T>> RandomAccessibleInterval<T> readGrayImage(String fn, T grayPixelType) {
        return new ImgOpener().openImgs(new FileLocation(fn), grayPixelType).get(0);
    }

    @Test
    public void readImageRangeForOtherCompression() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_lzw" + (i % 2 + 1) + ".tif";

            RandomAccessibleInterval<ARGBType> refImage = readRGBImage(testFileName);
            assertEquals(2, refImage.numDimensions());

            ImageAccess<? extends RGBPixelType<?>> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());

            compareRGBImages(refImage, testImage);
        }
    }

    private void compareRGBImages(RandomAccessibleInterval<ARGBType> refImage, ImageAccess<? extends RGBPixelType<?>> testImage) {
        assertArrayEquals(refImage.dimensionsAsLongArray(), testImage.getImageShape());

        Cursor<ARGBType> refImageCursor = new RandomAccessibleIntervalCursor<ARGBType>(refImage);
        int nPixels = 0;
        while(refImageCursor.hasNext()) {
            refImageCursor.fwd();
            ARGBType refPixel = refImageCursor.get();
            int refRed = ARGBType.red(refPixel.get());
            int refGreen = ARGBType.green(refPixel.get());
            int refBlue = ARGBType.blue(refPixel.get());
            RGBPixelType<?> testPixel = testImage.randomAccess().setPositionAndGet(refImageCursor.positionAsLongArray());
            Assert.assertEquals(refRed, testPixel.getRed());
            Assert.assertEquals(refGreen, testPixel.getGreen());
            Assert.assertEquals(refBlue, testPixel.getBlue());
            nPixels++;
        }
        assertEquals(testImage.size(), nPixels);
    }

    private <T extends NativeType<T>> void compareGrayImages(RandomAccessibleInterval<T> refImage, ImageAccess<T> testImage) {
        assertArrayEquals(refImage.dimensionsAsLongArray(), testImage.getImageShape());

        Cursor<T> refImageCursor = new RandomAccessibleIntervalCursor<T>(refImage);
        int nPixels = 0;
        while(refImageCursor.hasNext()) {
            refImageCursor.fwd();
            T refPixel = refImageCursor.get();
            T testPixel = testImage.randomAccess().setPositionAndGet(refImageCursor.positionAsLongArray());
            Assert.assertEquals(refPixel, testPixel);
            nPixels++;
        }
        assertEquals(testImage.size(), nPixels);
    }

}
