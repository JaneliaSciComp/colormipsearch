package org.janelia.colormipsearch.cds;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.SlowTest;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.GrayImageLoader;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.FileData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;

@Category(SlowTest.class)
public class VolumeSegmentationHelperTest {

    @Test
    public void generateLMSegmentedCDM() {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long startInit = System.currentTimeMillis();
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(
                        alignmentSpace,
                        new SWCImageLoader<>(
                                alignmentSpace,
                                0.5,
                                1,
                                new UnsignedShortType(255)).loadImage(FileData.fromString(emVolumeFileName))
                );
        long endInit = System.currentTimeMillis();
        System.out.printf("Completed initialization for %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endInit-startInit) / 1000.);
        assertNotNull(volumeSegmentationHelper);
        RandomAccessibleInterval<? extends RGBPixelType<?>> cdm = volumeSegmentationHelper.generateSegmentedCDM(
                new GrayImageLoader<>(alignmentSpace, new UnsignedShortType()).loadImage(FileData.fromString(lmVolumeFileName))
        );
        long endCDMGeneration = System.currentTimeMillis();
        System.out.printf("Completed CDM generation %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endCDMGeneration-endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayRGBImage(cdm);
    }

    @Test
    public void generateEMSegmentedCDM() {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long startInit = System.currentTimeMillis();
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(
                        alignmentSpace,
                        new GrayImageLoader<>(alignmentSpace, new UnsignedShortType()).loadImage(FileData.fromString(lmVolumeFileName))
                );
        long endInit = System.currentTimeMillis();
        System.out.printf("Completed initialization for %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endInit-startInit) / 1000.);
        assertNotNull(volumeSegmentationHelper);
        RandomAccessibleInterval<? extends RGBPixelType<?>> cdm = volumeSegmentationHelper.generateSegmentedCDM(
                new SWCImageLoader<>(
                        alignmentSpace,
                        1,
                        1,
                        new UnsignedShortType(255)).loadImage(FileData.fromString(emVolumeFileName))
        );
        long endCDMGeneration = System.currentTimeMillis();
        System.out.printf("Completed CDM generation %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endCDMGeneration-endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayRGBImage(cdm);
    }

}
