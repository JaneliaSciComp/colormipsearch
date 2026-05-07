package org.janelia.colormipsearch.cds;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.janelia.colormipsearch.SlowTests;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.algorithms.ScaleAlgorithm;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.io.SWCImageReader;
import org.janelia.colormipsearch.mips.DefaultImageLoader;
import org.janelia.colormipsearch.mips.ImageLoader;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowTests.class)
public class VolumeSegmentationHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelperTest.class);

    @Test
    public void generateLMSegmentedCDM() throws Exception {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        long startInit = System.currentTimeMillis();
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariants = Collections.singletonMap(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(
                        emVolumeFileName,
                        () -> {
                            try (InputStream is = new FileInputStream(emVolumeFileName)) {
                                return new SWCImageLoader(alignmentSpace, 0.5, 1).loadImage(emVolumeFileName, is);
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }
                )
        );
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(alignmentSpace, queryVariants, (img) -> {
                    TestUtils.displayImage(img, "TEST");
                });
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} segmentation helper in {} secs",
                emVolumeFileName,
                (endInit - startInit) / 1000.);
        assertTrue(volumeSegmentationHelper.isAvailable());

        ImageArray lmVolume = ImageReader.readImageArrayFromFile(lmVolumeFileName);
        ImageArray cdm = volumeSegmentationHelper.generateSegmentedCDM(lmVolume);
        long endCDMGeneration = System.currentTimeMillis();
        LOG.info("Completed CDM generation for {} in {} secs",
                emVolumeFileName,
                (endCDMGeneration - endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayImage(cdm, "LM Segmented CDM");
        TestUtils.waitForKey();
    }

    @Test
    public void generateEMSegmentedCDM() throws Exception {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        long startInit = System.currentTimeMillis();
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariants = Collections.singletonMap(
                ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(
                        lmVolumeFileName,
                        () -> {
                            try (InputStream is = new FileInputStream(lmVolumeFileName)) {
                                ImageLoader imageLoader = new DefaultImageLoader(alignmentSpace);
                                ImageArray imageArray = imageLoader.loadImage(lmVolumeFileName, is);

                                return ScaleAlgorithm.scaleVolume(
                                        imageArray,
                                        imageLoader.getExpectedWidth() / 2,
                                        imageLoader.getExpectedHeight() / 2,
                                        imageLoader.getExpectedDepth() / 2,
                                        Gray16ImageArray::new
                                );
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }
                )
        );
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(alignmentSpace, queryVariants, (img) -> {
                    TestUtils.displayImage(img, "TEST!!!");
                });
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} segmentation helper in {} secs",
                lmVolumeFileName,
                (endInit - startInit) / 1000.);
        assertTrue(volumeSegmentationHelper.isAvailable());

        ImageArray emVolume;
        try (InputStream is = new FileInputStream(emVolumeFileName)) {
            emVolume = new SWCImageLoader(alignmentSpace, 1, 1).loadImage(emVolumeFileName, is);
        }
        ImageArray cdm = volumeSegmentationHelper.generateSegmentedCDM(emVolume);
        long endCDMGeneration = System.currentTimeMillis();
        LOG.info("Completed CDM generation for {} in {} secs",
                emVolumeFileName,
                (endCDMGeneration - endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayImage(cdm, "EM Segmented CDM");
        TestUtils.waitForKey();
    }

    @Test
    public void generateLMSegmentedCDMForOpticLobe() throws Exception {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/100016_jrc18U_um.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/VT006415_100C09_AE_01-20171212_61_E6-f-CH2_01.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        long startInit = System.currentTimeMillis();
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariants = Collections.singletonMap(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(
                        emVolumeFileName,
                        () -> {
                            try (InputStream is = new FileInputStream(emVolumeFileName)) {
                                return SWCImageReader.readSWCStream(is,
                                        1210, 566, 174,
                                        0.5189161, 0.5189161, 1.0,
                                        1);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
        );
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(alignmentSpace, queryVariants);
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} segmentation helper in {} secs",
                emVolumeFileName,
                (endInit - startInit) / 1000.);
        assertTrue(volumeSegmentationHelper.isAvailable());

        ImageArray lmVolume = ImageReader.readImageArrayFromFile(lmVolumeFileName);
        ImageArray cdm = volumeSegmentationHelper.generateSegmentedCDM(lmVolume);
        long endCDMGeneration = System.currentTimeMillis();
        LOG.info("Completed CDM generation for {} in {} secs",
                emVolumeFileName,
                (endCDMGeneration - endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayImage(cdm, "Optic Lobe Segmented CDM");
    }
}
