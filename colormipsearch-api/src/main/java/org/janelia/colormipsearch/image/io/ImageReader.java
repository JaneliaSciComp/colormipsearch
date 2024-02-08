package org.janelia.colormipsearch.image.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.scif.img.ImgOpener;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.apache.commons.io.IOUtils;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.FileLocation;

public class ImageReader {
    private static final ImgOpener IMG_OPENER = new ImgOpener();

    public static <T> ImageAccess<T> readImage(String source, T backgroundPixel) {
        Img<T> image = IMG_OPENER.openImgs(new FileLocation(source), backgroundPixel).get(0);
        return new SimpleImageAccess<>(image, backgroundPixel);
    }

    public static <T> ImageAccess<T> readImageFromStream(InputStream source, T backgroundPixel) {
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
        Img<T> rgbImageCopy = new ArrayImgFactory<>(backgroundPixel).create(rgbImage);

        final IterableInterval<ARGBType> sourceIterable = Views.flatIterable( rgbImage );
        final IterableInterval<T> targetIterable = Views.flatIterable( rgbImageCopy );
        final Cursor<ARGBType> sourceCursor = sourceIterable.cursor();
        final Cursor<T> targetCursor = targetIterable.cursor();
        while (targetCursor.hasNext()) {
            ARGBType sourcePixel = sourceCursor.next();
            targetCursor.next().setFromRGB(
                    ARGBType.red(sourcePixel.get()),
                    ARGBType.green(sourcePixel.get()),
                    ARGBType.blue(sourcePixel.get())
            );
        }

        return new SimpleImageAccess<>(rgbImageCopy, backgroundPixel);
    }

    public static ImageAccess<ByteType> read8BitGrayImageFromStream(InputStream source) {
        return readImageFromStream(source, new ByteType());
    }

}
