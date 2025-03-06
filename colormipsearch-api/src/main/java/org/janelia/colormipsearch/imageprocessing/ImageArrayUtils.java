package org.janelia.colormipsearch.imageprocessing;

import java.io.InputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.Opener;
import ij.io.RandomAccessStream;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Image Utils.
 */
public class ImageArrayUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ImageArrayUtils.class);

    private enum ImageFormat {
        BMP,
        GIF,
        JPG,
        PNG,
        TIFF,
        WBMP,
        UNKNOWN
    }

    /**
     * Read an image array from an ImageJ ImagePlus object.
     *
     * @param imagePlus
     * @return
     */
    public static ImageArray<?> fromImagePlus(ImagePlus imagePlus) {
        ImageType type = ImageType.fromImagePlusType(imagePlus.getType());
        ImageProcessor ip = imagePlus.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        switch (type) {
            case GRAY8:
                return new ByteImageArray(type, width, height, (byte[]) ip.getPixels());
            case GRAY16:
                return new ShortImageArray(type, width, height, (short[]) ip.getPixels());
            case RGB:
                return new ColorImageArray(type, width, height, (int[]) ip.getPixels());
            default:
                throw new IllegalArgumentException("Unsupported image type: " + type);
        }
    }

    /**
     * Determine if the file identified by the given name is an image file. This is only based on the filename extension.
     *
     * @param name - file name
     * @return
     */
    public static boolean isImageFile(String name) {
        int extseparator = name.lastIndexOf('.');
        if (extseparator == -1) {
            return false;
        }
        String fext = name.substring(extseparator + 1);
        switch (fext.toLowerCase()) {
            case "bmp":
            case "gif":
            case "jpg":
            case "jpeg":
            case "png":
            case "tif":
            case "tiff":
            case "wbmp":
                return true;
            default:
                return false;
        }
    }

    /**
     * Read an image array from a byte stream.
     *
     * @param title  image title
     * @param name   image (file) name used only for determining the image encoding
     * @param stream image pixels stream
     * @return
     * @throws Exception
     */
    public static ImageArray<?> readImageArray(String title, String name, InputStream stream) throws Exception {
        ImageFormat format = getImageFormat(name);
        LOG.debug("Reading image array {} using {} format", name, format);
        ImagePlus imagePlus;
        switch (format) {
            case BMP:
            case GIF:
            case JPG:
            case PNG:
            case WBMP:
                imagePlus = readImagePlusWithImageIO(title, stream);
                break;
            case TIFF:
                imagePlus = readImagePlusWithTiffReader(title, stream);
                break;
            default:
                throw new IllegalArgumentException("Image '" + name + "' must be in PNG or TIFF format");
        }
        try {
            return fromImagePlus(imagePlus);
        } finally {
            if (imagePlus != null) imagePlus.close();
        }
    }

    /**
     * Read an image range from a stream. The range is actually used only for TIFF images with packbits compression.
     *
     * @param title
     * @param name
     * @param stream
     * @param start
     * @param end
     * @return
     * @throws Exception
     */
    public static ImageArray<?> readImageArrayRange(String title, String name, InputStream stream, long start, long end) throws Exception {
        ImageFormat format = getImageFormat(name);
        switch (format) {
            case BMP:
            case GIF:
            case JPG:
            case PNG:
            case WBMP:
                ImagePlus imagePlus = readImagePlusWithImageIO(title, stream);
                try {
                    return fromImagePlus(imagePlus);
                } finally {
                    imagePlus.close();
                }
            case TIFF:
                return readImageArrayRangeWithTiffReader(title, name, stream, start, end);
            default:
                throw new IllegalArgumentException("Image '" + name + "' must be in PNG or TIFF format");
        }
    }

    private static ImageFormat getImageFormat(String name) {
        String lowerCaseName = name.toLowerCase();

        if (lowerCaseName.endsWith(".bmp")) {
            return ImageFormat.BMP;
        } else if (lowerCaseName.endsWith(".gif")) {
            return ImageFormat.GIF;
        } else if (lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg")) {
            return ImageFormat.JPG;
        } else if (lowerCaseName.endsWith(".png")) {
            return ImageFormat.PNG;
        } else if (lowerCaseName.endsWith(".tiff") || lowerCaseName.endsWith(".tif")) {
            return ImageFormat.TIFF;
        } else if (lowerCaseName.endsWith(".wbmp")) {
            return ImageFormat.WBMP;
        }

        LOG.warn("Unrecognized format from {} - so far it only supports BMP, GIF, JPG, PNG, TIFF, and WBMP", name);
        return ImageFormat.UNKNOWN;
    }

    private static ImagePlus readImagePlusWithImageIO(String title, InputStream stream) throws Exception {
        return new ImagePlus(title, ImageIO.read(stream));
    }

    private static ImagePlus readImagePlusWithTiffReader(String title, InputStream stream) throws Exception {
        return new Opener().openTiff(stream, title);
    }

    private static ImageArray<?> readImageArrayRangeWithTiffReader(String title, String name, InputStream stream, long start, long end) throws Exception {
        int maskpos_st = (int) start * 3;
        int maskpos_ed = (int) end * 3;

        LocalTiffDecoder tfd = new LocalTiffDecoder(stream, title);
        RandomAccessStream ras = tfd.getRandomAccessStream();
        try {
            FileInfo[] fi_list = tfd.getTiffInfo();
            if (fi_list != null && fi_list[0] != null) {
                int width = fi_list[0].width;
                int height = fi_list[0].height;
                if (fi_list[0].compression != 5) {
                    ras.seek(0);
                    return fromImagePlus(readImagePlusWithTiffReader(title, ras));
                } else {
                    int bytesPerPixel = fi_list[0].getBytesPerPixel();
                    int dsize = width * height * bytesPerPixel;
                    int ioffset = 0;
                    byte[] img_bytearr = new byte[dsize];
                    for (int i = 0; i < fi_list[0].stripOffsets.length; i++) {
                        ras.seek(fi_list[0].stripOffsets[i]);
                        byte[] byteArray = new byte[fi_list[0].stripLengths[i]];
                        int read = 0, left = byteArray.length;
                        while (left > 0) {
                            int r = ras.read(byteArray, read, left);
                            if (r == -1) break;
                            read += r;
                            left -= r;
                        }
                        ioffset = packBitsUncompress(byteArray, img_bytearr, ioffset, maskpos_st, maskpos_ed);
                        if (ioffset >= maskpos_ed) {
                            break;
                        }
                    }
                    return new ColorImageArray(ImageType.fromImagePlusType(ImagePlus.COLOR_RGB), width, height, img_bytearr);
                }
            } else {
                return null;
            }
        } finally {
            if (ras != null)
                ras.close();
        }
    }

    private static int packBitsUncompress(byte[] input, byte[] output, int offset, int start, int end) {
        if (end == 0) end = Integer.MAX_VALUE;
        int index = 0;
        int pos = offset;
        while (pos < end && pos < output.length && index < input.length) {
            byte n = input[index++];
            if (n >= 0) { // 0 <= n <= 127
                byte[] b = new byte[n + 1];
                for (int i = 0; i < n + 1; i++)
                    b[i] = input[index++];
                if (pos >= start) {
                    System.arraycopy(b, 0, output, pos, b.length);
                } else if (pos < start && pos + b.length >= start) {
                    System.arraycopy(b, start - pos, output, start, b.length - start + pos);
                }
                pos += b.length;
            } else if (n != -128) { // -127 <= n <= -1
                int len = -n + 1;
                byte inp = input[index++];
                for (int i = 0; i < len; i++) {
                    if (pos >= start) {
                        output[pos++] = inp;
                    } else {
                        pos++;
                    }
                }
            }
        }
        return pos;
    }

    @SuppressWarnings("unchecked")
    public static ImageProcessor toImageProcessor(ImageArray<?> imageArray) {
        switch (imageArray.type) {
            case GRAY8:
                ImageArray<byte[]> byteImageArray = (ImageArray<byte[]>) imageArray;
                return new ByteProcessor(imageArray.width, imageArray.height, Arrays.copyOf(byteImageArray.getPixels(), byteImageArray.getPixelCount()));
            case GRAY16:
                ImageArray<short[]> shortImageArray = (ImageArray<short[]>) imageArray;
                return new ShortProcessor(imageArray.width, imageArray.height, Arrays.copyOf(shortImageArray.getPixels(), shortImageArray.getPixelCount()), null /* default color model */);
            default:
                int[] intImageBuffer = new int[imageArray.width * imageArray.height];
                for (int i = 0; i < intImageBuffer.length; i++) {
                    intImageBuffer[i] = imageArray.get(i);
                }
                return new ColorProcessor(imageArray.width, imageArray.height, intImageBuffer);
        }
    }

}
