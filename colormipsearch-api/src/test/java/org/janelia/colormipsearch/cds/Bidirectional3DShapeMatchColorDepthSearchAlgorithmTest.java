package org.janelia.colormipsearch.cds;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.mips.GrayImageLoader;
import org.janelia.colormipsearch.mips.ImageLoader;
import org.janelia.colormipsearch.mips.RGBImageLoader;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowTests.class)
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithmTest {
    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithmTest.class);

    private static final BiPredicate<long[]/*pos*/, long[]/*shape*/> SCALE_OR_LABEL_COND = (long[] pos, long[] shape) -> {
        if (pos.length != shape.length) {
            throw new IllegalArgumentException("Image coordinates and dimensions must be equal");
        }
        if (pos.length != 2) {
            throw new IllegalArgumentException("Image must be a 2D-image");
        }
        long imgWidth = shape[0];
        long x = pos[0];
        long y = pos[1];
        boolean isInsideColorScale = imgWidth > 270 && x >= imgWidth - 270 && y < 90;
        boolean isInsideNameLabel = x < 330 && y < 100;
        return isInsideColorScale || isInsideNameLabel;
    };

    @Test
    public void emToLmBidirectionalShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/0342_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02.png";

        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long start = System.currentTimeMillis();

        Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.SkeletonSWC,
                        new ComputeVariantImageSupplier<UnsignedShortType>() {
                            @Override
                            public String getName() {
                                return emVolumeFileName;
                            }

                            @Override
                            public RandomAccessibleInterval<UnsignedShortType> getImage() {
                                return new SWCImageLoader<>(
                                        alignmentSpace,
                                        0.5,
                                        1,
                                        new UnsignedShortType(255)).loadImage(FileData.fromString(emVolumeFileName));
                            }
                        }
                );

        Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> targetVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.Vol3DSegmentation,
                        new ComputeVariantImageSupplier<UnsignedShortType>() {
                            @Override
                            public String getName() {
                                return lmVolumeFileName;
                            }

                            @Override
                            public RandomAccessibleInterval<UnsignedShortType> getImage() {
                                return new GrayImageLoader<>(
                                        alignmentSpace,
                                        new UnsignedShortType()).loadImage(FileData.fromString(lmVolumeFileName));
                            }
                        }
                );
        RandomAccessibleInterval<ByteArrayRGBPixelType> queryImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(emCDM));
        long[] dims = queryImage.dimensionsAsLongArray();
        BiPredicate<long[], ByteArrayRGBPixelType> isScaleOrLabelRegion = (pos, pix) -> SCALE_OR_LABEL_COND.test(pos, dims);
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm bidirectionalShapeScoreAlg = new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                queryImage,
                queryVariantSuppliers,
                isScaleOrLabelRegion,
                null,
                alignmentSpace,
                20,
                20,
                false
        );
        long endInit = System.currentTimeMillis();
        RandomAccessibleInterval<ByteArrayRGBPixelType> targetImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(lmCDM));
        ShapeMatchScore shapeMatchScore =  bidirectionalShapeScoreAlg.calculateMatchingScore(
                targetImage,
                targetVariantSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("Completed EM2LM bidirectional shape score init in {} secs, score in {} secs, total {} secs",
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);
        assertNotNull(shapeMatchScore);
        assertTrue(shapeMatchScore.getBidirectionalAreaGap() != -1);
    }

    @Test
    public void lmToEmBidirectionalShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/0342_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02.png";

        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long start = System.currentTimeMillis();

        ImageLoader<UnsignedShortType> lmImageLoader = new GrayImageLoader<>(alignmentSpace, new UnsignedShortType());
        int[] expectedSize = lmImageLoader.getExpectedSize();
        Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.Vol3DSegmentation,
                        new ComputeVariantImageSupplier<UnsignedShortType>() {
                            @Override
                            public String getName() {
                                return lmVolumeFileName;
                            }

                            @Override
                            public RandomAccessibleInterval<UnsignedShortType> getImage() {
                                return ImageTransforms.scaleImage(lmImageLoader.loadImage(FileData.fromString(lmVolumeFileName)),
                                        new long[] { expectedSize[0] / 2, expectedSize[1] / 2, expectedSize[2] / 2},
                                        new UnsignedShortType()
                                );
                            }
                        }
                );
        Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> targetVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.SkeletonSWC,
                        new ComputeVariantImageSupplier<UnsignedShortType>() {
                            @Override
                            public String getName() {
                                return emVolumeFileName;
                            }

                            @Override
                            public RandomAccessibleInterval<UnsignedShortType> getImage() {
                                return new SWCImageLoader<>(
                                        alignmentSpace,
                                        1,
                                        1,
                                        new UnsignedShortType(255)).loadImage(FileData.fromString(emVolumeFileName));
                            }
                        }
                );

        RandomAccessibleInterval<ByteArrayRGBPixelType> queryImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(lmCDM));
        long[] dims = queryImage.dimensionsAsLongArray();
        BiPredicate<long[], ByteArrayRGBPixelType> isScaleOrLabelRegion = (pos, pix) -> SCALE_OR_LABEL_COND.test(pos, dims);
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm bidirectionalShapeScoreAlg = new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                queryImage,
                queryVariantSuppliers,
                isScaleOrLabelRegion,
                null,
                alignmentSpace,
                20,
                20,
                false
        );
        long endInit = System.currentTimeMillis();
        RandomAccessibleInterval<ByteArrayRGBPixelType> targetImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(emCDM));
        ShapeMatchScore shapeMatchScore =  bidirectionalShapeScoreAlg.calculateMatchingScore(
                targetImage,
                targetVariantSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("Completed LM2EM bidirectional shape score init in {} secs, score in {} secs, total {} secs",
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);
        assertNotNull(shapeMatchScore);
        assertTrue(shapeMatchScore.getBidirectionalAreaGap() != -1);
    }

}
