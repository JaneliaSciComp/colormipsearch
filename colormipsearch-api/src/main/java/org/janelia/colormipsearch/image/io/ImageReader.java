package org.janelia.colormipsearch.image.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.scif.img.ImgOpener;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.apache.commons.io.IOUtils;
import org.janelia.colormipsearch.image.ConvertPixelAccess;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.FileLocation;

public class ImageReader {
    private static final ImgOpener IMG_OPENER = new ImgOpener();

    public static <T> ImageAccess<T> readGrayImage(String source, T backgroundPixel) {
        Img<T> image = IMG_OPENER.openImgs(new FileLocation(source), backgroundPixel).get(0);
        return new SimpleImageAccess<>(image, backgroundPixel);
    }

    public static <T> ImageAccess<T> readGrayImageFromStream(InputStream source, T backgroundPixel) {
        try {
            BytesLocation bytesLocation = new BytesLocation(IOUtils.toByteArray(source));
            Img<T> image = IMG_OPENER.openImgs(bytesLocation, backgroundPixel).get(0);
            return new SimpleImageAccess<>(image, backgroundPixel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> readRGBImage(String source, T backgroundPixel) {
        Img<UnsignedByteType> image = IMG_OPENER.openImgs(new FileLocation(source), new UnsignedByteType(0)).get(0);
        return multichannelImageAsRGBImage(image, backgroundPixel);
    }

    public static <T extends RGBPixelType<T>> ImageAccess<T> readRGBImageFromStream(InputStream source, T backgroundPixel) {
        try {
            BytesLocation bytesLocation = new BytesLocation(IOUtils.toByteArray(source));
            Img<UnsignedByteType> image = IMG_OPENER.openImgs(bytesLocation, new UnsignedByteType(0)).get(0);
            return multichannelImageAsRGBImage(image, backgroundPixel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T extends RGBPixelType<T>> ImageAccess<T> multichannelImageAsRGBImage(Img<UnsignedByteType> image, T backgroundPixel) {
        RandomAccessibleInterval<ARGBType> rgbImage = Converters.mergeARGB(image, ColorChannelOrder.RGB);
        return new SimpleImageAccess<>(
                new ConvertPixelAccess<>(rgbImage.randomAccess(), backgroundPixel::fromARGBType),
                rgbImage,
                backgroundPixel
        );
    }

    public static ImageAccess<ByteType> read8BitGrayImageFromStream(InputStream source) {
        return readGrayImageFromStream(source, new ByteType());
    }

}
