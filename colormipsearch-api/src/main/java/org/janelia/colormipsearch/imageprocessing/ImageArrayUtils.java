package org.janelia.colormipsearch.imageprocessing;

import java.io.InputStream;

import javax.imageio.ImageIO;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.Opener;
import ij.io.RandomAccessStream;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.janelia.colormipsearch.image.ImageArray;
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
    public static ImageArray fromImagePlus(ImagePlus imagePlus) {
        ImageType type = ImageType.fromImagePlusType(imagePlus.getType());
        ImageProcessor ip = imagePlus.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        switch (type) {
            case GRAY8: {
                LOG.debug("Read {} GRAY8 {}x{} pixels", ((byte[]) ip.getPixels()).length, width, height);
                byte[] srcPixels = (byte[]) ip.getPixels();
                org.janelia.colormipsearch.image.ByteImageArray result =
                        new org.janelia.colormipsearch.image.ByteImageArray(width, height, 1, 1);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setIntVal(pi, srcPixels[pi] & 0xFF);
                }
                return result;
            }
            case GRAY16: {
                LOG.debug("Read {} GRAY16 {}x{} pixels", ((short[]) ip.getPixels()).length, width, height);
                short[] srcPixels = (short[]) ip.getPixels();
                org.janelia.colormipsearch.image.ShortImageArray result =
                        new org.janelia.colormipsearch.image.ShortImageArray(width, height, 1, 1);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setIntVal(pi, srcPixels[pi] & 0xFFFF);
                }
                return result;
            }
            case RGB: {
                LOG.debug("Read {} RGB {}x{} pixels", ((int[]) ip.getPixels()).length, width, height);
                int[] srcPixels = (int[]) ip.getPixels();
                org.janelia.colormipsearch.image.ByteImageArray result =
                        new org.janelia.colormipsearch.image.ByteImageArray(width, height, 1, 3);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setIntVal(pi, srcPixels[pi]); // setIntVal unpacks channels from packed int
                }
                return result;
            }
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
    public static ImageArray readImageArray(String title, String name, InputStream stream) throws Exception {
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
    public static ImageArray readImageArrayRange(String title, String name, InputStream stream, long start, long end) throws Exception {
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

    private static ImageArray readImageArrayRangeWithTiffReader(String title, String name, InputStream stream, long start, long end) throws Exception {
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
                    // Convert interleaved RGB bytes to planar ByteImageArray
                    int npixels = width * height;
                    org.janelia.colormipsearch.image.ByteImageArray result =
                            new org.janelia.colormipsearch.image.ByteImageArray(width, height, 1, 3);
                    for (int pi = 0; pi < npixels; pi++) {
                        result.setChannelVal(pi, 0, img_bytearr[pi * 3] & 0xFF);
                        result.setChannelVal(pi, 1, img_bytearr[pi * 3 + 1] & 0xFF);
                        result.setChannelVal(pi, 2, img_bytearr[pi * 3 + 2] & 0xFF);
                    }
                    return result;
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

    public static ImageProcessor toImageProcessor(ImageArray imageArray) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int npixels = width * height;
        if (imageArray instanceof org.janelia.colormipsearch.image.ShortImageArray) {
            short[] pixels = new short[npixels];
            for (int pi = 0; pi < npixels; pi++) {
                pixels[pi] = (short) (imageArray.get(pi) & 0xFFFF);
            }
            return new ShortProcessor(width, height, pixels, null);
        } else if (imageArray.getChannels() >= 3) {
            int[] pixels = new int[npixels];
            for (int pi = 0; pi < npixels; pi++) {
                pixels[pi] = imageArray.get(pi);
            }
            return new ColorProcessor(width, height, pixels);
        } else {
            byte[] pixels = new byte[npixels];
            for (int pi = 0; pi < npixels; pi++) {
                pixels[pi] = (byte) (imageArray.get(pi) & 0xFF);
            }
            return new ByteProcessor(width, height, pixels);
        }
    }

}
