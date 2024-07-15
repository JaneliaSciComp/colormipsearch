package org.janelia.colormipsearch.cds;

import java.io.IOException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.janelia.colormipsearch.image.TestUtils;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class VolumeSegmentationHelperTest {

    @SuppressWarnings("unchecked")
    @Test
    public void emSegmentedVolume() {
        long startSegment = System.currentTimeMillis();
        String fn = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        RandomAccessibleInterval<UnsignedIntType> segmentedVolume = (RandomAccessibleInterval<UnsignedIntType>) VolumeSegmentationHelper.prepareEMSegmentedVolume(
                fn,
                "JRC2018_Unisex_20x_HR"
        );
        long endSegment = System.currentTimeMillis();
        System.out.printf("Completed volume segmentation for %s in %fs\n",
                fn,
                (endSegment-startSegment) / 1000.);
        TestUtils.displayNumericImage(segmentedVolume);
        assertNotNull(segmentedVolume);
        TestUtils.waitForKey();
    }
}
