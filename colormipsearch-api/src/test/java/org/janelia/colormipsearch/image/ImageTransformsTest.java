package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Comparator;

import ij.ImagePlus;
import ij.Prefs;
import ij.io.Opener;
import ij.plugin.filter.RankFilters;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.Dilation;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Scale3DAlgorithm;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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
            Img<ByteArrayRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new ByteArrayRGBPixelType());
            RandomAccessibleInterval<ByteArrayRGBPixelType> mirroredTestImage = ImageTransforms.mirrorImage(testImage, 0);
            RandomAccessibleInterval<ByteArrayRGBPixelType> doubleMirroredTestImage = Views.invertAxis(mirroredTestImage, 0);
            Img<ByteArrayRGBPixelType> nativeMirroredImg = ImageAccessUtils.materializeAsNativeImg(
                    mirroredTestImage,
                    null,
                    new ByteArrayRGBPixelType()
            );
            long endTime = System.currentTimeMillis();
            System.out.println("Completed mirror for " + testFileName + " in " + (endTime - startTime) / 1000.);

            assertNotEquals(0, TestUtils.compareImages(mirroredTestImage, doubleMirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertNotEquals(0, TestUtils.compareImages(testImage, nativeMirroredImg, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertNotEquals(0, TestUtils.compareImages(testImage, mirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));
            assertEquals(0, TestUtils.compareImages(testImage, doubleMirroredTestImage, (Comparator<ByteArrayRGBPixelType>) rgbComparator));

            TestUtils.displayRGBImage(testImage);
            TestUtils.displayRGBImage(mirroredTestImage);
            TestUtils.displayRGBImage(nativeMirroredImg);
            TestUtils.displayRGBImage(doubleMirroredTestImage);
        }
    }

    @Test
    public void maxIntensityProjection() {
        String testFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        Img<UnsignedIntType> testImage = ImageReader.readImage(testFileName, new UnsignedIntType(0));
        for (int d = 0; d < 3; d++) {
            RandomAccessibleInterval<UnsignedIntType> projectionImg = ImageTransforms.maxIntensityProjection(
                    testImage,
                    UnsignedIntType::compareTo,
                    d,
                    testImage.min(d),
                    testImage.max(d)
            );
            assertEquals(2, projectionImg.numDimensions());
            switch (d) {
                case 0:
                    assertEquals(testImage.dimension(1), projectionImg.dimension(0));
                    assertEquals(testImage.dimension(2), projectionImg.dimension(1));
                    break;
                case 1:
                    assertEquals(testImage.dimension(0), projectionImg.dimension(0));
                    assertEquals(testImage.dimension(2), projectionImg.dimension(1));
                    break;
                case 2:
                    assertEquals(testImage.dimension(0), projectionImg.dimension(0));
                    assertEquals(testImage.dimension(1), projectionImg.dimension(1));
                    break;
            }
            Img<UnsignedIntType> nativeProjectionImage = ImageAccessUtils.materializeAsNativeImg(
                    projectionImg,
                    null,
                    new UnsignedIntType()
            );
            TestUtils.displayNumericImage(projectionImg);
            TestUtils.displayNumericImage(nativeProjectionImage);
        }
    }

    @Test
    public void maxFilterComparedWithImage1RankFilter() {
        int testRadius = 20;
        int[] testRadii = new int[2];
        Arrays.fill(testRadii, testRadius);
        Prefs.setThreads(1);
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            RandomAccessibleInterval<IntRGBPixelType> maxFilterRGBTestImage = ImageTransforms.dilateImage(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    testRadii
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
            maxFilter.rank(refImage.getProcessor(), testRadius - 1e-9, RankFilters.MAX);
            long maxFilterEndTime = System.currentTimeMillis();

            TestUtils.displayIJImage(refImage);
            TestUtils.displayRGBImage(nativeMaxFilterImg);

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
                    (endTime - startTime) / 1000.,
                    (maxFilterEndTime - maxFilterStartTime) / 1000.,
                    ndiffs,
                    (comparisonEndTime - comparisonStartTime) / 1000.);
        }
    }

    @Test
    public void maxFilterComparedWithImage1RankFilterWithAccessInterval() {
        int testRadius = 20;
        int[] testRadii = new int[2];
        Arrays.fill(testRadii, testRadius);
        Prefs.setThreads(1);
        for (int i = 0; i < 1; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            long startTime = System.currentTimeMillis();
            Interval accessInterval = Intervals.createMinMax(testRadius, 2 * testRadius, testImage.max(0) - testRadius + 1, testImage.max(1) - 2 * testRadius + 1);
            RandomAccessibleInterval<IntRGBPixelType> imageAccessMaxFilterRGBTestImage = ImageTransforms.dilateImageInterval(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    testRadii,
                    null
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
            TestUtils.displayRGBImage(nativeMaxFilterImg);

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
                    (endTime - startTime) / 1000.,
                    (maxFilterEndTime - maxFilterStartTime) / 1000.,
                    ndiffs,
                    (comparisonEndTime - comparisonStartTime) / 1000.);
        }
    }

    @Category({SlowTests.class})
    @Test
    public void maxFilterComparedWithImageJ1RankFilterAndImglib2Dilation() {
        int testRadius = 20;
        int[] testRadii = new int[2];
        Arrays.fill(testRadii, testRadius);
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            long imageAccessMaxFilterStartTime = System.currentTimeMillis();
            RandomAccessibleInterval<IntRGBPixelType> imageAccessMaxFilterRGBTestImage = ImageTransforms.dilateImage(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    testRadii
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
            TestUtils.displayRGBImage(nativeMaxFilterImg);
            TestUtils.displayRGBImage(img2Dilation);

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
                    (imageAccessMaxFilterEndTime - imageAccessMaxFilterStartTime) / 1000.,
                    (rankMaxFilterEndTime - rankMaxFilterStartTime) / 1000.,
                    (img2DilationEndTime - img2DilationStartTime) / 1000.,
                    nativeMaxFilterDiffs,
                    imgAccessMaxFilterDiffs,
                    img2DilationDiffs);
        }
    }

    @Test
    public void maxFilter2DRGBImagesWithDifferentRadii() {
        class TestData {
            final String fn;
            final int[] radii;

            TestData(String fn, int[] radii) {
                this.fn = fn;
                this.radii = radii;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif",
                        new int[]{15, 7}
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest2.tif",
                        new int[]{7, 15}
                ),
        };
        for (TestData td : testData) {
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(td.fn, new IntRGBPixelType());
            RandomAccessibleInterval<IntRGBPixelType> maxFilterRGBTestImage = ImageTransforms.dilateImage(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    td.radii
            );
            TestUtils.displayRGBImage(ImageAccessUtils.materializeAsNativeImg(maxFilterRGBTestImage, null, new IntRGBPixelType()));
            System.out.printf("Completed dilated native %s\n", td.fn);
            TestUtils.displayRGBImage(maxFilterRGBTestImage);
            System.out.printf("Completed dilated view %s\n", td.fn);
        }
    }

    @Test
    public void maxFilter1d() {
        int testRadius = 20;
        for (int i = 0; i < 2; i++) {
            String testFileName = "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest" + (i % 2 + 1) + ".tif";
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(testFileName, new IntRGBPixelType());
            RandomAccessibleInterval<IntRGBPixelType> maxFilterRGBXTestImage = ImageAccessUtils.materializeAsNativeImg(
                    ImageTransforms.dilateRGBImage1d(testImage, testRadius, 0),
                    null,
                    new IntRGBPixelType()
            );
            RandomAccessibleInterval<IntRGBPixelType> maxFilterRGBXandYTestImage = ImageTransforms.dilateRGBImage1d(
                    maxFilterRGBXTestImage,
                    testRadius,
                    1
            );
            long startTime = System.currentTimeMillis();
            Img<IntRGBPixelType> nativeMaxFilterXYImg = ImageAccessUtils.materializeAsNativeImg(
                    maxFilterRGBXandYTestImage,
                    null,
                    new IntRGBPixelType()
            );
            long endTime = System.currentTimeMillis();

            long startRefTime = System.currentTimeMillis();
            Img<IntRGBPixelType> refImage = MaxFilterAlgorithm.rgbMaxFilterInXandY(testImage, testRadius, 0);
            long endRefTime = System.currentTimeMillis();

            long ndiffs = TestUtils.countDiffs(refImage, nativeMaxFilterXYImg);

            TestUtils.displayRGBImage(nativeMaxFilterXYImg);
            TestUtils.displayRGBImage(refImage);

            System.out.printf("Completed maxFilter1d for %s in %f secs and %f secs using native traversal - found %d diffs.\n",
                    testFileName,
                    (endTime - startTime) / 1000.,
                    (endRefTime - startRefTime) / 1000.,
                    ndiffs);

            assertEquals(0, ndiffs);
        }
    }

    @Category({SlowTests.class})
    @Test
    public void maxFilter3DImagesWithDifferentRadii() {
        class TestData {
            final String fn;
            final int[] radii;
            final Interval interval;

            TestData(String fn, int[] radii, Interval interval) {
                this.fn = fn;
                this.radii = radii;
                this.interval = interval;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[]{5, 5, 3},
                        new FinalInterval(
                                new long[]{500, 50, 35},
                                new long[]{550, 100, 65}
                        )
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[]{10, 5, 10},
                        new FinalInterval(
                                new long[]{500, 50, 35},
                                new long[]{650, 150, 65}
                        )
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[]{5, 10, 10},
                        new FinalInterval(
                                new long[]{500, 50, 35},
                                new long[]{650, 150, 65}
                        )
                )
        };
        for (TestData td : testData) {
            Img<UnsignedShortType> testImage = ImageReader.readImage(td.fn, new UnsignedShortType());
            long startTime = System.currentTimeMillis();
            RandomAccessibleInterval<UnsignedShortType> maxFilterRGBTestImage = ImageTransforms.dilateImageInterval(
                    testImage,
                    () -> new IntensityPixelHistogram<>(new UnsignedShortType(), 16),
                    td.radii,
                    td.interval
            );
            RandomAccessibleInterval<UnsignedShortType> nativeMaxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    maxFilterRGBTestImage,
                    null,
                    new UnsignedShortType()
            );
            long endTime1 = System.currentTimeMillis();
            Img<UnsignedShortType> kernelBasedMaxFilterImg = MaxFilterAlgorithm.dilate(
                    Views.interval(testImage, td.interval),
                    td.radii[0], td.radii[1], td.radii[2],
                    new ArrayImgFactory<>(new UnsignedShortType())
            );
            long endTime2 = System.currentTimeMillis();
            long ndiffs = TestUtils.countDiffs(kernelBasedMaxFilterImg, nativeMaxFilterImg);

            System.out.printf("Completed %s dilation with radii %s in %f secs using histogram traversal and in %f secs using kernel traversal found %d diffs\n",
                    td.fn,
                    Arrays.toString(td.radii),
                    (endTime1 - startTime) / 1000.,
                    (endTime2 - endTime1) / 1000.,
                    ndiffs);
            assertEquals(0, ndiffs);
            TestUtils.displayNumericImage(Views.interval(testImage, td.interval));
            TestUtils.displayNumericImage(kernelBasedMaxFilterImg);
            TestUtils.displayNumericImage(nativeMaxFilterImg);
        }
    }

    @Test
    public void scale2DImages() {
        class TestData {
            final String fn;
            final double[] scaleFactors;

            TestData(String fn, double[] scaleFactors) {
                this.fn = fn;
                this.scaleFactors = scaleFactors;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest1.tif",
                        new double[]{2, 2}
                ),
                new TestData(
                        "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest2.tif",
                        new double[]{0.5, 0.5}
                )
        };
        for (TestData td : testData) {
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(td.fn, new IntRGBPixelType());
            RandomAccessibleInterval<UnsignedIntType> numericTestImage = ImageTransforms.rgbToIntensityTransformation(
                    testImage,
                    new UnsignedIntType(),
                    false
            );
            long startTime = System.currentTimeMillis();
            RandomAccessibleInterval<UnsignedIntType> scaledNumericTestImage = ImageTransforms.scaleImage(
                    numericTestImage,
                    new long[]{
                            (long) (testImage.dimension(0) * td.scaleFactors[0]),
                            (long) (testImage.dimension(1) * td.scaleFactors[1]),
                    },
                    new UnsignedIntType()
            );
            long endScaleTime = System.currentTimeMillis();
            RandomAccessibleInterval<UnsignedIntType> inverseScaledNumericTestImage = ImageTransforms.scaleImage(
                    scaledNumericTestImage,
                    testImage.dimensionsAsLongArray(),
                    new UnsignedIntType()
            );
            long endInvScaleTime = System.currentTimeMillis();
            System.out.printf("Completed scale for %s in %fs and inverse scale in %f\n",
                    td.fn,
                    (endScaleTime - startTime) / 1000.,
                    (endInvScaleTime - endScaleTime) / 1000.);
            TestUtils.displayRGBImage(testImage);
            TestUtils.displayNumericImage(numericTestImage);
            TestUtils.displayNumericImage(scaledNumericTestImage);
            TestUtils.displayNumericImage(inverseScaledNumericTestImage);
        }
    }

    @Category({SlowTests.class})
    @Test
    public void scale3DImages() {
        class TestData {
            final String fn;
            final double[] scaleFactors;

            TestData(String fn, double[] scaleFactors) {
                this.fn = fn;
                this.scaleFactors = scaleFactors;
            }
        }
        TestData[] testData = new TestData[]{
                new TestData(
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new double[]{0.5, 0.5, 0.5}
                ),
        };
        for (TestData td : testData) {
            Img<UnsignedIntType> testImage = ImageReader.readImage(td.fn, new UnsignedIntType());
            long startTime = System.currentTimeMillis();
            RandomAccessibleInterval<UnsignedIntType> scaledTestImage = ImageTransforms.scaleImage(
                    testImage,
                    new long[]{
                            (long) (testImage.dimension(0) * td.scaleFactors[0]),
                            (long) (testImage.dimension(1) * td.scaleFactors[1]),
                            (long) (testImage.dimension(2) * td.scaleFactors[2]),
                    },
                    new UnsignedIntType()
            );
            RandomAccessibleInterval<UnsignedIntType> nativeScaledImg = ImageAccessUtils.materializeAsNativeImg(
                    scaledTestImage,
                    null,
                    new UnsignedIntType()
            );
            long endScaleTime = System.currentTimeMillis();

            RandomAccessibleInterval<UnsignedIntType> scale3DTestImage = Scale3DAlgorithm.scale3DImage(
                    testImage,
                    (int) (testImage.dimension(0) * td.scaleFactors[0]),
                    (int) (testImage.dimension(1) * td.scaleFactors[1]),
                    (int) (testImage.dimension(2) * td.scaleFactors[2]),
                    new UnsignedIntType()
            );

            long end3dScaleTime = System.currentTimeMillis();

            RandomAccessibleInterval<UnsignedIntType> inverseScaledTestImage = ImageTransforms.scaleImage(
                    nativeScaledImg,
                    testImage.dimensionsAsLongArray(),
                    new UnsignedIntType()
            );
            TestUtils.displayNumericImage(testImage);
            TestUtils.displayNumericImage(nativeScaledImg);
            TestUtils.displayNumericImage(scale3DTestImage);
            TestUtils.displayNumericImage(inverseScaledTestImage);
            long ndiffs = TestUtils.countDiffs(scale3DTestImage, nativeScaledImg);
            System.out.printf("Complete %s scale in %f secs with ScaleRandomAccess and in %f secs with 3d scale - found %d diffs\n",
                    td.fn,
                    (endScaleTime - startTime) / 1000.,
                    (end3dScaleTime - endScaleTime) / 1000.,
                    ndiffs
            );
        }
    }

}
