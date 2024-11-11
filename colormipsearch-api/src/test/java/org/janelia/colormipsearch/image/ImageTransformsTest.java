package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.Opener;
import ij.plugin.Filters3D;
import ij.plugin.filter.RankFilters;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.Dilation;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Scale3DAlgorithm;
import org.janelia.colormipsearch.image.algorithms.tensor.TensorBasedMaxFilterAlgorithm;
import org.janelia.colormipsearch.image.algorithms.tensor.TensorBasedMaxFilterAlgorithmTF;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ImageTransformsTest {

    private static final Logger LOG = LoggerFactory.getLogger(ImageTransformsTest.class);

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
            LOG.info("Completed mirror for {} in {} secs", testFileName, (endTime - startTime) / 1000.);

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
            LOG.info("Completed maxFilter for {} in {} vs {} using IJ1 rankFilter. Found {} diffs with IJ1 maxfilter in {} secs",
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
            LOG.info("Completed maxFilter for {} in {} vs {} using IJ1 rankFilter. Found {} diffs with IJ1 maxfilter in {} secs",
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
        for (int i = 1; i < 2; i++) {
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
                    Prefs.getThreads()
            );
            long img2DilationEndTime = System.currentTimeMillis();
            Img<IntRGBPixelType> tensorDilation = TensorBasedMaxFilterAlgorithm.dilate2D(
                    nativeTestImage,
                    testRadius, testRadius,
                    new ArrayImgFactory<>(new IntRGBPixelType()),
                    "cpu"
            );
            long tensorDilationEndTime = System.currentTimeMillis();
            TestUtils.displayIJImage(refIJ1Image);
            TestUtils.displayRGBImage(imageAccessMaxFilterRGBTestImage); // Image 0
            TestUtils.displayRGBImage(nativeMaxFilterImg); // Image 1
            TestUtils.displayRGBImage(img2Dilation); // Image 2
            TestUtils.displayRGBImage(tensorDilation); // Image 3

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
            LOG.info("Completed maxFilter for {} in {} vs {} using IJ1 rankFilter vs {} with IJ2-algorithm dilation vs {} with Tensor-dilation. " +
                            "There are {} and {} with IJ1 maxfilter and {} diffs with IJ2 dilation",
                    testFileName,
                    (imageAccessMaxFilterEndTime - imageAccessMaxFilterStartTime) / 1000.,
                    (rankMaxFilterEndTime - rankMaxFilterStartTime) / 1000.,
                    (img2DilationEndTime - img2DilationStartTime) / 1000.,
                    (tensorDilationEndTime - img2DilationEndTime) / 1000.,
                    nativeMaxFilterDiffs,
                    imgAccessMaxFilterDiffs,
                    img2DilationDiffs);
        }
        TestUtils.waitForKey();
    }

    @Test
    public void maxFilter2DRGBImagesWithTensorFlow() {
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
            long startHistogramDilationTime = System.currentTimeMillis();
            RandomAccessibleInterval<IntRGBPixelType> imageAccessMaxFilterRGBTestImage = ImageTransforms.dilateImage(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    td.radii
            );
            Img<IntRGBPixelType> nativeMaxFilterImg = ImageAccessUtils.materializeAsNativeImg(
                    imageAccessMaxFilterRGBTestImage,
                    null,
                    new IntRGBPixelType()
            );
            long endHistogramDilationTime = System.currentTimeMillis();
            RandomAccessibleInterval<IntRGBPixelType> maxFilterUsingTensorImage = TensorBasedMaxFilterAlgorithmTF.dilate2D(
                    testImage,
                    td.radii[0],
                    td.radii[1],
                    new ArrayImgFactory<>(new IntRGBPixelType()),
                    "gpu"
            );
            long endTensorDilationTime = System.currentTimeMillis();
            long ndiffs = TestUtils.countDiffs(nativeMaxFilterImg, maxFilterUsingTensorImage);
            LOG.info("Completed maxFilter {} with histogram ({} secs) and tensorflow ({} secs) - found {} diffs",
                    td.fn,
                    (endHistogramDilationTime - startHistogramDilationTime) / 1000.,
                    (endTensorDilationTime - endHistogramDilationTime) / 1000.,
                    ndiffs);

            TestUtils.displayRGBImage(nativeMaxFilterImg);
            TestUtils.displayRGBImage(maxFilterUsingTensorImage);
        }
        TestUtils.waitForKey();
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
//                new TestData(
//                        "src/test/resources/colormipsearch/api/imageprocessing/minmaxTest2.tif",
//                        new int[]{7, 15}
//                ),
        };
        for (TestData td : testData) {
            Img<IntRGBPixelType> testImage = ImageReader.readRGBImage(td.fn, new IntRGBPixelType());
            RandomAccessibleInterval<IntRGBPixelType> maxFilterRGBTestImage = ImageTransforms.dilateImage(
                    testImage,
                    () -> new RGBPixelHistogram<>(new IntRGBPixelType()),
                    td.radii
            );
            RandomAccessibleInterval<IntRGBPixelType> maxFilterUsingTensorImage = TensorBasedMaxFilterAlgorithm.dilate2D(
                    testImage,
                    td.radii[0],
                    td.radii[1],
                    new ArrayImgFactory<>(new IntRGBPixelType()),
                    "gpu"
            );
            TestUtils.displayRGBImage(ImageAccessUtils.materializeAsNativeImg(maxFilterRGBTestImage, null, new IntRGBPixelType()));
            LOG.info("Completed dilated native {}", td.fn);
//            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(td.radii[1], td.radii[0]);
//            int[] maskArrays = kernelMask.getKernelMask(3, 3);
//            int kernelSize = (2 * td.radii[1] + 1) * (2 * td.radii[0] + 1);

            TestUtils.displayRGBImage(maxFilterRGBTestImage);
            TestUtils.displayRGBImage(maxFilterUsingTensorImage);
            LOG.info("Completed dilated view {}", td.fn);
        }
        TestUtils.waitForKey();
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

            LOG.info("Completed maxFilter1d for {} in {} secs and {} secs using native traversal - found {} diffs.",
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
//                new TestData(
//                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
//                        new int[]{5, 5, 3},
//                        new FinalInterval(
//                                new long[]{500, 50, 35},
//                                new long[]{550, 100, 65}
//                        )
//                ),
//                new TestData(
//                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
//                        new int[]{10, 5, 10},
//                        new FinalInterval(
//                                new long[]{500, 50, 35},
//                                new long[]{650, 150, 65}
//                        )
//                ),
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

            LOG.info("Completed {} dilation with radii {} in {} secs using histogram traversal and in {} secs using kernel traversal found {} diffs",
                    td.fn,
                    Arrays.toString(td.radii),
                    (endTime1 - startTime) / 1000.,
                    (endTime2 - endTime1) / 1000.,
                    ndiffs);
            assertEquals(0, ndiffs);
            TestUtils.displayNumericImage(Views.interval(testImage, td.interval));
            TestUtils.displayNumericImage(nativeMaxFilterImg);
            TestUtils.displayNumericImage(kernelBasedMaxFilterImg);
        }
    }

    @Test
    public void maxFilter3DImagesWithDifferentRadiiUsingTensorOperations() {
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
                        new int[]{5, 7, 9},
                        new FinalInterval(
                                new long[]{450, 20, 25},
                                new long[]{650, 180, 85}
                        )
                )
        };
        for (TestData td : testData) {
            Img<UnsignedShortType> testImage = ImageReader.readImage(td.fn, new UnsignedShortType());
            long startTime1 = System.currentTimeMillis();
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
            Img<UnsignedShortType> kernelBasedMaxFilterImg = TensorBasedMaxFilterAlgorithmTF.dilate3D(
                    Views.interval(testImage, td.interval),
                    td.radii[0], td.radii[1], td.radii[2],
                    new ArrayImgFactory<>(new UnsignedShortType()),
                    "gpu"
            );
            long endTime2 = System.currentTimeMillis();
            long ndiffs = TestUtils.countDiffs(kernelBasedMaxFilterImg, nativeMaxFilterImg);
            LOG.info("Completed {} dilation with radii {} in {} secs using histogram traversal and in {} secs using kernel NDArray traversal - found {} diffs",
                    td.fn,
                    Arrays.toString(td.radii),
                    (endTime1 - startTime1) / 1000.,
                    (endTime2 - endTime1) / 1000.,
                    ndiffs);
            TestUtils.displayNumericImage(Views.interval(testImage, td.interval));
            TestUtils.displayNumericImage(nativeMaxFilterImg);
            TestUtils.displayNumericImage(kernelBasedMaxFilterImg);
        }
        TestUtils.waitForKey();
    }

    @Category({SlowTests.class})
    @Test
    public void maxFilter3DComparisonBetweenImageJ1andImgLib2() {
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
                        "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd",
                        new int[]{5, 5, 3}
                ),
        };
        for (TestData td : testData) {
            Img<UnsignedShortType> testImage = ImageReader.readImage(td.fn, new UnsignedShortType());
            long startTime = System.currentTimeMillis();
            ImagePlus imp = TestUtils.img2ImagePlus(testImage, "Test Image");

            long endImg2ImagePlus = System.currentTimeMillis();
            LOG.info("Completed conversion to ImagePlus in {} secs", (endImg2ImagePlus - startTime) / 1000.);

            compareImageJDilationWithHistogramBasedDilation(
                    testImage, imp,
                    td.radii[0], td.radii[1], td.radii[2]);

            compareImageJDilationWithImg2CursorBasedDilation(
                    testImage, imp,
                    td.radii[0], td.radii[1], td.radii[2]);
        }
    }

    private void compareImageJDilationWithHistogramBasedDilation(Img<UnsignedShortType> testImage,
                                                                 ImagePlus imagePlus,
                                                                 int rx, int ry, int rz) {
        Img<UnsignedShortType> dilatedTestImg = testImage.factory().create(testImage);
        long startHistogramBasedDilation = System.currentTimeMillis();
        ImageTransforms.parallelDilateImage(
                testImage,
                dilatedTestImg,
                () -> new IntensityPixelHistogram<>(new UnsignedShortType(), 16),
                new int[]{rx, ry, rz},
                new ForkJoinPool()
        );
        long endHistogramBasedDilation = System.currentTimeMillis();
        LOG.info("Completed sliding window based parallel dilation in {} secs",
                (endHistogramBasedDilation - startHistogramBasedDilation) / 1000.);

        long startFilter3D = System.currentTimeMillis();
        ImageStack dilatedStack = Filters3D.filter(imagePlus.getStack(), Filters3D.MAX, rx, ry, rz);
        ImagePlus dilatedImp = new ImagePlus("Filter3D Dilated image", dilatedStack);
        long endFilter3D = System.currentTimeMillis();
        LOG.info("Completed max filter 3D in {} secs", (endFilter3D - startFilter3D) / 1000.);

        long ndiffs = TestUtils.countDiffs(dilatedTestImg, ImageJFunctions.wrapReal(dilatedImp));
        LOG.info("Found {} diffs between filter 3d and sliding window based dilation", ndiffs);
        assertEquals(0, ndiffs);

        TestUtils.displayIJImage(dilatedImp);
        TestUtils.displayNumericImage(dilatedTestImg);
    }

    private void compareImageJDilationWithImg2CursorBasedDilation(Img<UnsignedShortType> testImage,
                                                                  ImagePlus imagePlus,
                                                                  int rx, int ry, int rz) {
        long startFilter3D = System.currentTimeMillis();
        ImageStack dilatedStack = Filters3D.filter(imagePlus.getStack(), Filters3D.MAX, rx, ry, rz);
        ImagePlus dilatedImp = new ImagePlus("Filter3D Dilated image", dilatedStack);
        long endFilter3D = System.currentTimeMillis();
        LOG.info("Completed max filter 3D in {} secs", (endFilter3D - startFilter3D) / 1000.);

        ExecutorService executorService = new ForkJoinPool(Prefs.getThreads());
        long startImglib2MaxFilter = System.currentTimeMillis();
        Img<UnsignedShortType> dilatedTestImg = MaxFilterAlgorithm.dilateMT(
                testImage,
                rx, ry, rz,
                new ArrayImgFactory<>(new UnsignedShortType()),
                executorService
        );
        long endImglib2MaxFilter = System.currentTimeMillis();
        LOG.info("Completed imglib2 dilateMT in {} secs using {} threads",
                (endImglib2MaxFilter - startImglib2MaxFilter) / 1000., Prefs.getThreads());

        long ndiffs = TestUtils.countDiffs(dilatedTestImg, ImageJFunctions.wrapReal(dilatedImp));
        LOG.info("Found {} diffs between filter 3d and sliding window based dilation", ndiffs);
        assertEquals(0, ndiffs);

        TestUtils.displayIJImage(dilatedImp);
        TestUtils.displayNumericImage(dilatedTestImg);
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
            LOG.info("Completed scale for {} in {} secs and inverse scale in {} secs",
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
            LOG.info("Complete {} scale in {} secs with ScaleRandomAccess and in {} secs with 3d scale - found {} diffs",
                    td.fn,
                    (endScaleTime - startTime) / 1000.,
                    (end3dScaleTime - endScaleTime) / 1000.,
                    ndiffs
            );
        }
    }

}
