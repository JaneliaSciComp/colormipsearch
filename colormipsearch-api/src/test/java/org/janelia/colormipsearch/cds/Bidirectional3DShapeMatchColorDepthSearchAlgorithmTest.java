package org.janelia.colormipsearch.cds;

import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
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
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm bidirectionalShapeScoreAlg = new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(emCDM)),
                queryVariantSuppliers,
                null,
                alignmentSpace,
                20,
                20,
                false
        );
        long endInit = System.currentTimeMillis();
        ShapeMatchScore shapeMatchScore =  bidirectionalShapeScoreAlg.calculateMatchingScore(
                new RGBImageLoader<>(alignmentSpace, new ByteArrayRGBPixelType()).loadImage(FileData.fromString(lmCDM)),
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
