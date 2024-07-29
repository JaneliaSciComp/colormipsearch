package org.janelia.colormipsearch.cds;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.GrayImageLoader;
import org.janelia.colormipsearch.mips.ImageLoader;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.FileData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

@Category(SlowTests.class)
public class VolumeSegmentationHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelperTest.class);

    @Test
    public void generateLMSegmentedCDM() {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long startInit = System.currentTimeMillis();
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(
                        alignmentSpace,
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
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} segmentation helper in {} secs",
                emVolumeFileName,
                (endInit-startInit) / 1000.);
        assertNotNull(volumeSegmentationHelper);
        RandomAccessibleInterval<? extends RGBPixelType<?>> cdm = volumeSegmentationHelper.generateSegmentedCDM(
                new GrayImageLoader<>(alignmentSpace, new UnsignedShortType()).loadImage(FileData.fromString(lmVolumeFileName))
        );
        long endCDMGeneration = System.currentTimeMillis();
        LOG.info("Completed CDM generation {} segmentation helper in {} secs",
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
        ImageLoader<UnsignedShortType> lmImageLoader = new GrayImageLoader<>(alignmentSpace, new UnsignedShortType());
        int[] expectedSize = lmImageLoader.getExpectedSize();
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(
                        alignmentSpace,
                        new ComputeVariantImageSupplier<UnsignedShortType>() {
                            @Override
                            public String getName() {
                                return lmVolumeFileName;
                            }

                            @Override
                            public RandomAccessibleInterval<UnsignedShortType> getImage() {
                                return ImageTransforms.scaleImage(
                                        lmImageLoader.loadImage(FileData.fromString(lmVolumeFileName)),
                                        new long[] { expectedSize[0] / 2, expectedSize[1] / 2, expectedSize[2] / 2},
                                        new UnsignedShortType()
                                );
                            }
                        }
                );
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} segmentation helper in {} secs",
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
        LOG.info("Completed CDM generation {} segmentation helper in {} secs",
                emVolumeFileName,
                (endCDMGeneration-endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayRGBImage(cdm);
    }

}
