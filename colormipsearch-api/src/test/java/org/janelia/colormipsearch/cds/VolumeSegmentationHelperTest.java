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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowTests.class)
public class VolumeSegmentationHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelperTest.class);

    @Test
    public void generateLMSegmentedCDM() {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String refLMcdmFileName = "src/test/resources/colormipsearch/api/cdsearch/cdms/lm-segmented-cdm.png";
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
        LOG.info("Completed initialization for {} LM segmentation helper in {} secs",
                emVolumeFileName,
                (endInit - startInit) / 1000.);
        assertTrue(volumeSegmentationHelper.isAvailable());

        ImageArray lmVolume = ImageReader.readImageArrayFromFile(lmVolumeFileName);
        ImageArray cdm = volumeSegmentationHelper.generateSegmentedCDM(lmVolume);
        long endCDMGeneration = System.currentTimeMillis();
        ImageArray refLMcdm = ImageReader.readImageArrayFromFile(refLMcdmFileName);
        int ndiffs = TestUtils.countDiffs(refLMcdm, cdm);
        LOG.info("Completed LM CDM generation for {} in {} secs - found {} diffs",
                emVolumeFileName,
                (endCDMGeneration - endInit) / 1000.,
                ndiffs
        );
        // Categorize diffs: how many are ref-only (ref nonzero, cdm zero), cdm-only, or both nonzero but different
        int refOnly = 0, cdmOnly = 0, bothDiff = 0, maxChDiff = 0;
        for (int pi = 0; pi < refLMcdm.getWidth() * refLMcdm.getHeight(); pi++) {
            int rp = refLMcdm.getPackedIntValAtIndex(pi) & 0x00FFFFFF;
            int cp = cdm.getPackedIntValAtIndex(pi) & 0x00FFFFFF;
            if (rp != cp) {
                if (rp == 0) cdmOnly++;
                else if (cp == 0) refOnly++;
                else bothDiff++;
                int dr = Math.abs(((rp >> 16) & 0xff) - ((cp >> 16) & 0xff));
                int dg = Math.abs(((rp >> 8) & 0xff) - ((cp >> 8) & 0xff));
                int db = Math.abs((rp & 0xff) - (cp & 0xff));
                int md = Math.max(dr, Math.max(dg, db));
                if (md > maxChDiff) maxChDiff = md;
            }
        }
        LOG.info("NDIFFS={} refOnly={} cdmOnly={} bothDiff={} maxChannelDiff={}", ndiffs, refOnly, cdmOnly, bothDiff, maxChDiff);
        assertNotNull(cdm);
        assertEquals(0, ndiffs);
        TestUtils.displayImage(cdm, "REF LM Segmented CDM");
        TestUtils.displayImage(cdm, "LM Segmented CDM");
        TestUtils.waitForKey();
    }

    @Test
    public void generateEMSegmentedCDM() throws Exception {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String refEMcdmFileName = "src/test/resources/colormipsearch/api/cdsearch/cdms/em-segmented-cdm.png";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";

        long startInit = System.currentTimeMillis();
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariants = Collections.singletonMap(
                ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(
                        lmVolumeFileName,
                        () -> {
                            try (InputStream is = new FileInputStream(lmVolumeFileName)) {
                                ImageLoader imageLoader = new DefaultImageLoader(alignmentSpace);
                                return imageLoader.loadImage(lmVolumeFileName, is);
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
        LOG.info("Completed initialization for {} EM segmentation helper in {} secs",
                lmVolumeFileName,
                (endInit - startInit) / 1000.);
        assertTrue(volumeSegmentationHelper.isAvailable());

        ImageArray emVolume;
        try (InputStream is = new FileInputStream(emVolumeFileName)) {
            emVolume = new SWCImageLoader(alignmentSpace, 1, 1).loadImage(emVolumeFileName, is);
        }
        ImageArray cdm = volumeSegmentationHelper.generateSegmentedCDM(emVolume);
        long endCDMGeneration = System.currentTimeMillis();
        ImageArray refEMcdm = ImageReader.readImageArrayFromFile(refEMcdmFileName);
        int ndiffs = TestUtils.countDiffs(refEMcdm, cdm);
        LOG.info("Completed EM CDM generation for {} in {} secs - found {} diffs",
                emVolumeFileName,
                (endCDMGeneration - endInit) / 1000.,
                ndiffs
        );
        assertNotNull(cdm);
        TestUtils.displayImage(refEMcdm, "REF EM Segmented CDM");
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
                                return new SWCImageLoader(alignmentSpace, 1, 1).loadImage(emVolumeFileName, is);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
        );
        VolumeSegmentationHelper volumeSegmentationHelper =
                new VolumeSegmentationHelper(alignmentSpace, queryVariants);
        long endInit = System.currentTimeMillis();
        LOG.info("Completed initialization for {} EM Optic lobe segmentation helper in {} secs",
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
