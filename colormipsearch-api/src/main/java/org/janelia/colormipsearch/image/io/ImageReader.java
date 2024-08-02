package org.janelia.colormipsearch.image.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.scif.img.ImgOpener;
import loci.formats.IFormatReader;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.FileLocation;

public class ImageReader {
    private static final ImgOpener IMG_OPENER = new ImgOpener();

    public static <T extends NativeType<T> & RealType<T>> Img<T> readSWC(String swcSource,
                                                                         int width, int height, int depth,
                                                                         double xSpacing, double ySpacing, double zSpacing, double radius,
                                                                         T foregroundPixel) {
        T backgroundPixel;
        backgroundPixel = foregroundPixel.createVariable();
        backgroundPixel.setZero();
        if (foregroundPixel.valueEquals(backgroundPixel)) {
            foregroundPixel.setOne();
        }
        ImgFactory<T> imgFactory = new ArrayImgFactory<>(backgroundPixel);
        Img<T> image = imgFactory.create(width, height, depth);
        SWCImageReader.readSWCSkeleton(swcSource, image, xSpacing, ySpacing, zSpacing, radius, foregroundPixel);
        return image;
    }

    public static <T extends NativeType<T> & RealType<T>> Img<T> readSWCStream(InputStream swcSourceStream,
                                                                               int width, int height, int depth,
                                                                               double xSpacing, double ySpacing, double zSpacing, double radius,
                                                                               T foregroundPixel) {
        T backgroundPixel;
        backgroundPixel = foregroundPixel.createVariable();
        backgroundPixel.setZero();
        if (foregroundPixel.valueEquals(backgroundPixel)) {
            foregroundPixel.setOne();
        }
        ImgFactory<T> imgFactory = new ArrayImgFactory<>(backgroundPixel);
        Img<T> image = imgFactory.create(width, height, depth);
        SWCImageReader.readSWCSkeleton(swcSourceStream, image, xSpacing, ySpacing, zSpacing, radius, foregroundPixel);
        return image;
    }

    public static <T extends NativeType<T> & RealType<T>> Img<T> readImage(String source, T backgroundPixel) {
        Img<T> image;
        if (StringUtils.endsWithIgnoreCase(source, ".nrrd")) {
            image = readNRRD(source, backgroundPixel);
        } else {
            image = IMG_OPENER.openImgs(new FileLocation(source), backgroundPixel).get(0);
        }
        return image;
    }

    public static <T extends NativeType<T> & RealType<T>> Img<T> readNRRD(String source, T type) {
        try (IFormatReader reader = new loci.formats.ImageReader()) {
            reader.setId(source);
            int bitsPerPixel = reader.getBitsPerPixel();
            int width = reader.getSizeX();
            int height = reader.getSizeY();
            int depth = reader.getSizeZ();
            int zSlicePixels = width * height;

            // Create an Img object with the appropriate type and dimensions
            ImgFactory<T> imgFactory = new ArrayImgFactory<>(type);
            Img<T> img = imgFactory.create(width, height, depth);
            int bytesPerPixel = bitsPerPixel / 8;
            byte[] zDataBytes = new byte[zSlicePixels * bytesPerPixel];

            Cursor<T> cursor = img.cursor();
            for (int z = 0; z < depth; z++) {
                reader.openBytes(z, zDataBytes);
                ByteBuffer zDataBuffer = ByteBuffer.wrap(zDataBytes).order(ByteOrder.BIG_ENDIAN);
                for (int pi = 0; pi < zSlicePixels; pi++) {
                    T pixValue = cursor.next();
                    if (bitsPerPixel <= 8) {
                        int p = zDataBuffer.get() & 0xff;
                        ((IntegerType<?>) pixValue).setInteger(p);
                    } else if (bitsPerPixel <= 16) {
                        int p = zDataBuffer.getShort() & 0xffff;
                        ((IntegerType<?>) pixValue).setInteger(p);
                    } else if (bitsPerPixel <= 32) {
                        float p = zDataBuffer.getFloat();
                        pixValue.setReal(p);
                    } else {
                        throw new IllegalArgumentException("Unsupported bit depth: " + bitsPerPixel);
                    }
                }
            }
            return img;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T extends Type<T>> Img<T> readImageFromStream(InputStream source, T backgroundPixel) {
        try {
            BytesLocation bytesLocation = new BytesLocation(IOUtils.toByteArray(source));
            return IMG_OPENER.openImgs(bytesLocation, backgroundPixel).get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T extends RGBPixelType<T>> Img<T> readRGBImage(String source, T backgroundPixel) {
        Img<UnsignedByteType> image = IMG_OPENER.openImgs(new FileLocation(source), new UnsignedByteType(0)).get(0);
        return ImageAccessUtils.createRGBImageFromMultichannelImg(image, backgroundPixel);
    }

    public static <T extends RGBPixelType<T>> Img<T> readRGBImageFromStream(InputStream source, T backgroundPixel) {
        try {
            BytesLocation bytesLocation = new BytesLocation(IOUtils.toByteArray(source));
            Img<UnsignedByteType> image = IMG_OPENER.openImgs(bytesLocation, new UnsignedByteType(0)).get(0);
            return ImageAccessUtils.createRGBImageFromMultichannelImg(image, backgroundPixel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Img<ByteType> read8BitGrayImageFromStream(InputStream source) {
        return readImageFromStream(source, new ByteType());
    }

}