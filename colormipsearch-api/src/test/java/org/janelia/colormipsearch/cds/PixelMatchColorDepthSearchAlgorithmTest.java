package org.janelia.colormipsearch.cds;

import java.util.Collections;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.ConvertPixelAccess;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PixelMatchColorDepthSearchAlgorithmTest {

    private static final int CDMIP_WIDTH = 1210;
    private static final int CDMIP_HEIGHT = 566;

    @Test
    public void cdsMatchWithDirectBestMatch() {
        ImageAccess<ByteArrayRGBPixelType> mask = createTestMaskImage();
        ImageAccess<ByteArrayRGBPixelType> target = createTestTargetImage(false);
        PixelMatchColorDepthSearchAlgorithm<ByteArrayRGBPixelType, ?> cdsAlg = new PixelMatchColorDepthSearchAlgorithm<>(
                mask,
                100,
                100,
                true,
                2,
                2);
        PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(target, Collections.emptyMap(), Collections.emptyMap());
        assertEquals(20000, cdsScore.getScore());
        assertFalse(cdsScore.isMirrored());
    }

    @Test
    public void cdsMatchWithMirroredBestMatch() {
        ImageAccess<ByteArrayRGBPixelType> mask = createTestMaskImage();
        ImageAccess<ByteArrayRGBPixelType> target = createTestTargetImage(true);
        PixelMatchColorDepthSearchAlgorithm<ByteArrayRGBPixelType, ?> cdsAlg = new PixelMatchColorDepthSearchAlgorithm<>(
                mask,
                100,
                100, true,
                2,
                2);
        PixelMatchScore cdsScore = cdsAlg.calculateMatchingScore(target, Collections.emptyMap(), Collections.emptyMap());
        assertEquals(20000, cdsScore.getScore());
        assertTrue(cdsScore.isMirrored());
    }

    private ImageAccess<ByteArrayRGBPixelType> createTestMaskImage() {
        final Img<UnsignedByteType> img = new ArrayImgFactory<>(new UnsignedByteType())
                .create(CDMIP_WIDTH, CDMIP_HEIGHT, 3);
        for (int y = 300; y < 400; y++) {
            for (int x = 100; x < 200; x++) {
                img.randomAccess().setPositionAndGet(x, y, 0).set(254);
                img.randomAccess().setPositionAndGet(x, y, 1).set(255);
                img.randomAccess().setPositionAndGet(x, y, 2).set(0);
            }
            for (int x = 200; x < 300; x++) {
                img.randomAccess().setPositionAndGet(x, y, 0).set(54);
                img.randomAccess().setPositionAndGet(x, y, 1).set(255);
                img.randomAccess().setPositionAndGet(x, y, 2).set(201);
            }
        }
        return asRGBImage(img);
    }

    private ImageAccess<ByteArrayRGBPixelType> createTestTargetImage(boolean mirror) {
        final Img<UnsignedByteType> img = new ArrayImgFactory<>(new UnsignedByteType())
                .create(CDMIP_WIDTH, CDMIP_HEIGHT, 3);

        for (int y = 300; y < 400; y++) {
            for (int x = 102; x < 202; x++) {
                int xToUse = mirror ? CDMIP_WIDTH - x - 1 : x;
                img.randomAccess().setPositionAndGet(xToUse, y, 0).set(254);
                img.randomAccess().setPositionAndGet(xToUse, y, 1).set(255);
                img.randomAccess().setPositionAndGet(xToUse, y, 2).set(0);
            }
            for (int x = 202; x < 302; x++) {
                int xToUse = mirror ? CDMIP_WIDTH - x - 1 : x;
                img.randomAccess().setPositionAndGet(xToUse, y, 0).set(54);
                img.randomAccess().setPositionAndGet(xToUse, y, 1).set(255);
                img.randomAccess().setPositionAndGet(xToUse, y, 2).set(201);
            }
        }
        return asRGBImage(img);
    }

    private static ImageAccess<ByteArrayRGBPixelType> asRGBImage(Img<UnsignedByteType> image) {
        RandomAccessibleInterval<ARGBType> rgbImage = Converters.mergeARGB(image, ColorChannelOrder.RGB);
        ByteArrayRGBPixelType backgroundPixel = new ByteArrayRGBPixelType();
        return new SimpleImageAccess<>(
                new ConvertPixelAccess<>(
                        rgbImage.randomAccess(),
                        p -> backgroundPixel.createFromRGB(ARGBType.red(p.get()), ARGBType.green(p.get()), ARGBType.blue(p.get()))),
                rgbImage,
                backgroundPixel
        );
    }

}
