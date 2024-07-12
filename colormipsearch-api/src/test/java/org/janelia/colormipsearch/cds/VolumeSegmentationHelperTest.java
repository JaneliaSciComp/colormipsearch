package org.janelia.colormipsearch.cds;

import java.io.IOException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.TestUtils;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class VolumeSegmentationHelperTest {

    @Test
    public void emSegmentedVolume() {
        long startSegment = System.currentTimeMillis();
        String fn = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        ImageAccess<UnsignedIntType> segmentedVolume = (ImageAccess<UnsignedIntType>) VolumeSegmentationHelper.prepareEMSegmentedVolume(
                fn,
                "JRC2018_Unisex_20x_HR"
        );
        ImageAccess<UnsignedIntType> nativeSegmentedVolume = ImageAccessUtils.materialize(
                segmentedVolume,
                null
        );
        long endSegment = System.currentTimeMillis();
        System.out.printf("Completed volume segmentation for %s in %fs\n",
                fn,
                (endSegment-startSegment) / 1000.);
        TestUtils.displayNumericImage(nativeSegmentedVolume);
        assertNotNull(nativeSegmentedVolume);
        try {
            System.in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
