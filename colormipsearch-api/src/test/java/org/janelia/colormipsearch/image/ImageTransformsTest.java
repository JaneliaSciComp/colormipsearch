package org.janelia.colormipsearch.image;

import java.util.Comparator;

import ij.ImagePlus;
import ij.Prefs;
import ij.io.Opener;
import ij.plugin.filter.RankFilters;
import net.imglib2.Interval;
import net.imglib2.algorithm.morphology.Dilation;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.img.Img;
import net.imglib2.util.Intervals;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ImageTransformsTest {

    private final Comparator<? extends RGBPixelType<?>> rgbComparator = new TestUtils.RGBComparator<>();

    @SuppressWarnings("unchecked")
    @Test
    public void mirrorImage() {
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/compressed_pack" + (i % 2 + 1) + ".tif";

            long startTime = System.currentTimeMillis();
            ImageAccess<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            ImageAccess<ByteArrayRGBPixelType> mirroredTestImage = ImageTransforms.createGeomTransformation(testImage, new MirrorTransform(testImage.getImageShape(), 0));
            ImageAccess<ByteArrayRGBPixelType> doubleMirroredTestImage = ImageTransforms.createGeomTransformation(mirroredTestImage, new MirrorTransform(mirroredTestImage.getImageShape(), 0));

            Img<ByteArrayRGBPixelType> mirroredImg = ImageAccessUtils.materializeAsNativeImg(
                    mirroredTestImage,
                    null,
                    new ByteArrayRGBPixelType()
            );

            long endTime = System.currentTimeMillis();
            System.out.println("Completed mirror for " + testFileName + " in " + (endTime-startTime)/1000.);

            assertNotEquals(0, TestUtils.compareImages(testImage, mirroredImg, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertNotEquals(0, TestUtils.compareImages(testImage, mirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertEquals(0, TestUtils.compareImages(testImage, doubleMirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertNotEquals(0, TestUtils.compareImages(mirroredTestImage, doubleMirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));

            TestUtils.displayRGBImage(testImage);
            TestUtils.displayRGBImage(mirroredTestImage);
            TestUtils.displayRGBImage(new SimpleImageAccess<>(mirroredImg));
            TestUtils.displayRGBImage(doubleMirroredTestImage);

        }
    }

    @Test
    public void maxFilterComparedWithImage1RankFilter() {
        int testRadius = 20;
        Prefs.setThreads(1);
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageAccess<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            ImageAccess<IntRGBPixelType> maxFilterRGBTestImage = ImageTransforms.createHyperSphereDilationTransformation(
                    testImage,
                    testRadius
            );
            long startTime = System.currentTimeMillis();
            Img<IntRGBPixelType> nativeMaxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    maxFilterRGBTestImage,
                    null,
                    new IntRGBPixelType()
            );
            long endTime = System.currentTimeMillis();

            ImagePlus refImage = new Opener().openTiff(testFileName, 1);
            RankFilters maxFilter = new RankFilters();
            long maxFilterStartTime = System.currentTimeMillis();
            // IJ1 creates the circular kernel a bit differently by qdding 1e-10 to the radius
            // so in order for my test to work I subtract a very small value (1e-10) from the test radius
            maxFilter.rank(refImage.getProcessor(), testRadius - 1e-10, RankFilters.MAX);
            long maxFilterEndTime = System.currentTimeMillis();

            TestUtils.displayIJImage(refImage);
            TestUtils.displayRGBImage(new SimpleImageAccess<>(nativeMaxFilterImg));

            long comparisonStartTime = System.currentTimeMillis();
            int ndiffs = 0;
            for (int r = 0; r < refImage.getHeight(); r++) {
                for (int c = 0; c < refImage.getWidth(); c++) {
                    int refPixel = refImage.getProcessor().get(c, r) & 0xffffff;
                    int testPixel = nativeMaxFilterImg.getAt(c, r).getInteger() & 0xffffff;
                    if (refPixel != testPixel) {
                        ndiffs++;
                    }
                }
            }
            long comparisonEndTime = System.currentTimeMillis();
            assertEquals("Pixel differences", 0, ndiffs);
            System.out.printf("Completed maxFilter for %s in %f vs %f using IJ1 rankFilter. " +
                            "Found %d diffs with IJ1 maxfilter in %fs\n",
                    testFileName,
                    (endTime-startTime) / 1000.,
                    (maxFilterEndTime-maxFilterStartTime) / 1000.,
                    ndiffs,
                    (comparisonEndTime-comparisonStartTime)/1000.);
        }
    }

    @Test
    public void maxFilterComparedWithImage1RankFilterWithAccessInterval() {
        int testRadius = 20;
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageAccess<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            long startTime = System.currentTimeMillis();
            Interval accessInterval = Intervals.createMinMax(2*testRadius, 2*testRadius, testImage.max(0)-2*testRadius+1, testImage.max(1)-2*testRadius+1);
            ImageAccess<IntRGBPixelType> imageAccessMaxFilterRGBTestImage = ImageTransforms.createHyperSphereDilationTransformation(
                    testImage,
                    testRadius
            );
            Img<IntRGBPixelType> nativeMaxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    imageAccessMaxFilterRGBTestImage,
                    accessInterval,
                    new IntRGBPixelType()
            );
            long endTime = System.currentTimeMillis();

            ImagePlus refImage = new Opener().openTiff(testFileName, 1);
            RankFilters maxFilter = new RankFilters();
            long maxFilterStartTime = System.currentTimeMillis();
            // IJ1 creates the circular kernel a bit differently by qdding 1e-10 to the radius
            // so in order for my test to work I subtract a very small value (1e-10) from the test radius
            maxFilter.rank(refImage.getProcessor(), testRadius - 1e-10, RankFilters.MAX);
            long maxFilterEndTime = System.currentTimeMillis();

            TestUtils.displayIJImage(refImage);
            TestUtils.displayRGBImage(new SimpleImageAccess<>(nativeMaxFilterImg));

            long comparisonStartTime = System.currentTimeMillis();
            int ndiffs = 0;
            for (int r = (int) accessInterval.min(1); r < accessInterval.max(1); r++) {
                for (int c = (int) accessInterval.min(0); c < accessInterval.max(1); c++) {
                    int refPixel = refImage.getProcessor().get(c, r) & 0xffffff;
                    int testPixel = imageAccessMaxFilterRGBTestImage.getAt(c, r).getInteger() & 0xffffff;
                    if (refPixel != testPixel) {
                        ndiffs++;
                    }
                }
            }
            long comparisonEndTime = System.currentTimeMillis();
            assertEquals("Pixel differences", 0, ndiffs);
            System.out.printf("Completed maxFilter for %s in %f vs %f using IJ1 rankFilter. " +
                            "Found %d diffs with IJ1 maxfilter in %fs\n",
                    testFileName,
                    (endTime-startTime) / 1000.,
                    (maxFilterEndTime-maxFilterStartTime) / 1000.,
                    ndiffs,
                    (comparisonEndTime-comparisonStartTime)/1000.);
        }
    }

    @Test
    public void maxFilterComparedWithImageJ1RankFilterAndImglib2Dilation() {
        int testRadius = 20;
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            ImageAccess<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            long imageAccessMaxFilterStartTime = System.currentTimeMillis();
            ImageAccess<IntRGBPixelType> imageAccessMaxFilterRGBTestImage = ImageTransforms.createHyperSphereDilationTransformation(
                    testImage, testRadius
            );
            Img<IntRGBPixelType> nativeMaxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    imageAccessMaxFilterRGBTestImage,
                    null,
                    new IntRGBPixelType()
            );
            long imageAccessMaxFilterEndTime = System.currentTimeMillis();

            ImagePlus refIJ1Image = new Opener().openTiff(testFileName, 1);
            RankFilters rankMaxFilter = new RankFilters();
            long rankMaxFilterStartTime = System.currentTimeMillis();
            // IJ1 creates the circular kernel a bit differently by qdding 1e-10 to the radius
            // so in order for my test to work I subtract a very small value (1e-10) from the test radius
            rankMaxFilter.rank(refIJ1Image.getProcessor(), testRadius - 1e-10, RankFilters.MAX);
            long rankMaxFilterEndTime = System.currentTimeMillis();

            Img<IntRGBPixelType> nativeTestImage = ImageAccessUtils.materializeAsNativeImg(
                    testImage,
                    null,
                    new IntRGBPixelType()
            );
            long img2DilationStartTime = System.currentTimeMillis();
            Img<IntRGBPixelType> img2Dilation = Dilation.dilate(
                    nativeTestImage,
                    new HyperSphereShape(testRadius),
                    1
            );
            long img2DilationEndTime = System.currentTimeMillis();
            TestUtils.displayIJImage(refIJ1Image);
            TestUtils.displayRGBImage(imageAccessMaxFilterRGBTestImage);
            TestUtils.displayRGBImage(new SimpleImageAccess<>(nativeMaxFilterImg));
            TestUtils.displayRGBImage(new SimpleImageAccess<>(img2Dilation, new IntRGBPixelType()));

            int nativeMaxFilterDiffs = 0, imgAccessMaxFilterDiffs = 0, img2DilationDiffs = 0;
            for (int r = 0; r < refIJ1Image.getHeight(); r++) {
                for (int c = 0; c < refIJ1Image.getWidth(); c++) {
                    int refIJ1Pixel = refIJ1Image.getProcessor().get(c, r) & 0xffffff;
                    int imgAccessMaxFilterPixel = imageAccessMaxFilterRGBTestImage.getAt(c, r).getInteger() & 0xffffff;
                    int nativeMaxFilterPixel = nativeMaxFilterImg.getAt(c, r).getInteger() & 0xffffff;
                    int img2DilationPixel = img2Dilation.getAt(c, r).getInteger() & 0xffffff;
                    if (refIJ1Pixel != nativeMaxFilterPixel) {
                        nativeMaxFilterDiffs++;
                    }
                    if (refIJ1Pixel != imgAccessMaxFilterPixel) {
                        imgAccessMaxFilterDiffs++;
                    }
                    if (refIJ1Pixel != img2DilationPixel) {
                        img2DilationDiffs++;
                    }
                }
            }
            assertEquals("Pixel differences after converting max filter to native image", 0, nativeMaxFilterDiffs);
            assertEquals("Pixel differences without converting max filter image access", 0, imgAccessMaxFilterDiffs);
            System.out.printf("Completed maxFilter for %s in %f vs %f using IJ1 rankFilter vs %f. " +
                            "There are %d and %d with IJ1 maxfilter and %d diffs with IJ2 dilation\n",
                    testFileName,
                    (imageAccessMaxFilterEndTime-imageAccessMaxFilterStartTime) / 1000.,
                    (rankMaxFilterEndTime-rankMaxFilterStartTime) / 1000.,
                    (img2DilationEndTime-img2DilationStartTime) / 1000.,
                    nativeMaxFilterDiffs,
                    imgAccessMaxFilterDiffs,
                    img2DilationDiffs);
        }
    }

}
