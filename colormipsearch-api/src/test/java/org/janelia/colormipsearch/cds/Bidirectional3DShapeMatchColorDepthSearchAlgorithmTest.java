package org.janelia.colormipsearch.cds;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.GrayImageLoader;
import org.janelia.colormipsearch.mips.RGBImageLoader;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Bidirectional3DShapeMatchColorDepthSearchAlgorithmTest {
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
    public void bidirectionalShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/0342_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02.png";

        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long start = System.currentTimeMillis();

        Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.SkeletonSWC,
                        () -> new SWCImageLoader<>(
                                alignmentSpace,
                                0.5,
                                1,
                                new UnsignedShortType()).loadImage(FileData.fromString(emVolumeFileName))
                );

        Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantSuppliers =
                ImmutableMap.of(
                        ComputeFileType.Vol3DSegmentation,
                        () -> new GrayImageLoader<>(
                                alignmentSpace,
                                new UnsignedShortType()).loadImage(FileData.fromString(lmVolumeFileName))
                );
        RandomAccessibleInterval<ByteArrayRGBPixelType> queryImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(emCDM));
        long[] dims = queryImage.dimensionsAsLongArray();
        BiPredicate<long[], ByteArrayRGBPixelType> isScaleOrLabelRegion = (pos, pix) -> SCALE_OR_LABEL_COND.test(pos, dims);
        RandomAccessibleInterval<ByteArrayRGBPixelType> queryImageWithMaskedLabels = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.maskPixelsMatchingCond(queryImage, isScaleOrLabelRegion, null),
                null,
                new ByteArrayRGBPixelType()
        );
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm bidirectionalShapeScoreAlg = new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                queryImageWithMaskedLabels,
                queryVariantSuppliers,
                null,
                alignmentSpace,
                20,
                20,
                false
        );
        long endInit = System.currentTimeMillis();
        RandomAccessibleInterval<ByteArrayRGBPixelType> targetImage = new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(lmCDM));
        RandomAccessibleInterval<ByteArrayRGBPixelType> targetImageWithMaskedLabels = ImageTransforms.maskPixelsMatchingCond(targetImage, isScaleOrLabelRegion, null);

        ShapeMatchScore shapeMatchScore =  bidirectionalShapeScoreAlg.calculateMatchingScore(
                targetImageWithMaskedLabels,
                targetVariantSuppliers
        );

        long end = System.currentTimeMillis();
        System.out.printf("Completed bidirectional shape score init in %f secs, score in %f secs, total %f secs\n",
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);
        assertNotNull(shapeMatchScore);
        assertTrue(shapeMatchScore.getBidirectionalAreaGap() != -1);
        TestUtils.waitForKey();
    }
}
