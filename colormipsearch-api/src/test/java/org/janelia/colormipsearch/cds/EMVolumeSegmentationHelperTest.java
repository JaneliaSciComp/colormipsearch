package org.janelia.colormipsearch.cds;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class EMVolumeSegmentationHelperTest {

    @Test
    public void generateLMSegmentedCDM() {
        String emVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmVolumeFileName = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";
        String alignmentSpace = "JRC2018_Unisex_20x_HR";
        long startInit = System.currentTimeMillis();
        EMVolumeSegmentationHelper emVolumeSegmentationHelper = new EMVolumeSegmentationHelper(emVolumeFileName, alignmentSpace);
        long endInit = System.currentTimeMillis();
        System.out.printf("Completed initialization for %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endInit-startInit) / 1000.);
        assertNotNull(emVolumeSegmentationHelper);
        RandomAccessibleInterval<IntRGBPixelType> cdm = emVolumeSegmentationHelper.generateSegmentedCDM(lmVolumeFileName, new UnsignedShortType());
        long endCDMGeneration = System.currentTimeMillis();
        System.out.printf("Completed initialization for %s segmentation helper in %fs\n",
                emVolumeFileName,
                (endCDMGeneration-endInit) / 1000.);
        assertNotNull(cdm);
        TestUtils.displayRGBImage(cdm);
        TestUtils.waitForKey();
    }
}
