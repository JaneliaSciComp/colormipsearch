package org.janelia.colormipsearch.image.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;

import com.google.common.io.ByteStreams;

import ij.io.FileInfo;
import ij.io.RandomAccessStream;
import loci.common.ByteArrayHandle;
import loci.common.Location;
import loci.formats.IFormatReader;
import org.janelia.colormipsearch.image.FloatImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.Gray8ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.MultiChannelByteImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads images into ImageArray.
 * Uses ImageJ Opener for TIFF (fast), ImageIO for PNG/JPG/etc, and Bio-Formats for NRRD and other formats.
 */
public class ImageReader {

    private static final Logger LOG = LoggerFactory.getLogger(ImageReader.class);

    public static ImageArray readImageArrayFromStream(String name, InputStream stream) throws Exception {
        if (isTiff(name)) {
            return readWithTiffDecoder(name, stream);
        } else if (isStandardImage(name)) {
            BufferedImage bi = ImageIO.read(stream);
            if (bi == null) {
                throw new IllegalStateException("Failed to read image: " + name);
            }
            return fromBufferedImage(bi);
        } else {
            return readWithBioFormats(name, stream);
        }
    }

    public static ImageArray readImageArrayFromFile(String name) {
        try {
            if (isTiff(name)) {
                try (InputStream is = new FileInputStream(name)) {
                    return readImageArrayFromStream(name, is);
                }
            } else if (isStandardImage(name)) {
                BufferedImage bi = ImageIO.read(new File(name));
                if (bi == null) {
                    throw new IllegalStateException("Failed to read image: " + name);
                }
                return fromBufferedImage(bi);
            } else {
                return readWithBioFormats(name, null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error reading image: " + name, e);
        }
    }

    private static ImageArray fromBufferedImage(BufferedImage bi) {
        int width = bi.getWidth();
        int height = bi.getHeight();
        int spatialSize = width * height;
        int type = bi.getType();

        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
            RGBByteImageArray res = new RGBByteImageArray(width, height, 1);
            int[] pixels = bi.getRGB(0, 0, width, height, null, 0, width);
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setPackedIntValAtIndex(pi, pixels[pi]);
            }
            return res;
        } else if (type == BufferedImage.TYPE_BYTE_GRAY) {
            Gray8ImageArray res = new Gray8ImageArray(width, height, 1);
            byte[] pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setChannelIntValAtIndex(pi, 0, pixels[pi] & 0xFF);
            }
            return res;
        } else if (type == BufferedImage.TYPE_USHORT_GRAY) {
            Gray16ImageArray res = new Gray16ImageArray(width, height, 1);
            short[] pixels = ((java.awt.image.DataBufferUShort) bi.getRaster().getDataBuffer()).getData();
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setChannelIntValAtIndex(pi, 0, pixels[pi] & 0xFFFF);
            }
            return res;
        } else if (type == BufferedImage.TYPE_3BYTE_BGR) {
            RGBByteImageArray res = new RGBByteImageArray(width, height, 1);
            byte[] pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
            for (int pi = 0; pi < spatialSize; pi++) {
                int b = pixels[3 * pi] & 0xFF;
                int g = pixels[3 * pi + 1] & 0xFF;
                int r = pixels[3 * pi + 2] & 0xFF;
                res.setPackedIntValAtIndex(pi, (r << 16) | (g << 8) | b);
            }
            return res;
        } else {
            // Fallback: convert via getRGB
            RGBByteImageArray res = new RGBByteImageArray(width, height, 1);
            int[] pixels = bi.getRGB(0, 0, width, height, null, 0, width);
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setPackedIntValAtIndex(pi, pixels[pi]);
            }
            return res;
        }
    }

    private static ImageArray readWithTiffDecoder(String name, InputStream stream) throws Exception {
        LocalTiffDecoder tfd = new LocalTiffDecoder(stream, name);
        RandomAccessStream ras = tfd.getRandomAccessStream();
        try {
            FileInfo[] fi_list = tfd.getTiffInfo();
            if (fi_list == null || fi_list[0] == null) {
                return null;
            }
            FileInfo fi = fi_list[0];
            int width = fi.width;
            int height = fi.height;
            int bytesPerPixel = fi.getBytesPerPixel();
            int dsize = width * height * bytesPerPixel;
            byte[] imgBytes = new byte[dsize];

            if (fi.compression == FileInfo.PACK_BITS) {
                int ioffset = 0;
                for (int i = 0; i < fi.stripOffsets.length; i++) {
                    ras.seek(fi.stripOffsets[i]);
                    byte[] stripData = readFully(ras, fi.stripLengths[i]);
                    ioffset = packBitsUncompress(stripData, imgBytes, ioffset, 0, -1);
                }
            } else if (fi.compression == FileInfo.LZW || fi.compression == FileInfo.LZW_WITH_DIFFERENCING) {
                int ioffset = 0;
                for (int i = 0; i < fi.stripOffsets.length; i++) {
                    ras.seek(fi.stripOffsets[i]);
                    byte[] stripData = readFully(ras, fi.stripLengths[i]);
                    byte[] decompressed = lzwUncompress(stripData);
                    if (fi.compression == FileInfo.LZW_WITH_DIFFERENCING) {
                        applyHorizontalDifferencing(decompressed, bytesPerPixel, width);
                    }
                    int len = Math.min(decompressed.length, dsize - ioffset);
                    System.arraycopy(decompressed, 0, imgBytes, ioffset, len);
                    ioffset += len;
                }
            } else if (fi.compression == FileInfo.ZIP) {
                int ioffset = 0;
                java.util.zip.Inflater inflater = new java.util.zip.Inflater();
                for (int i = 0; i < fi.stripOffsets.length; i++) {
                    ras.seek(fi.stripOffsets[i]);
                    byte[] stripData = readFully(ras, fi.stripLengths[i]);
                    inflater.reset();
                    inflater.setInput(stripData);
                    int remaining = dsize - ioffset;
                    int n = inflater.inflate(imgBytes, ioffset, remaining);
                    ioffset += n;
                }
            } else {
                // Uncompressed: read raw strip data
                int ioffset = 0;
                for (int i = 0; i < fi.stripOffsets.length; i++) {
                    ras.seek(fi.stripOffsets[i]);
                    int len = Math.min(fi.stripLengths[i], dsize - ioffset);
                    byte[] stripData = readFully(ras, len);
                    System.arraycopy(stripData, 0, imgBytes, ioffset, stripData.length);
                    ioffset += stripData.length;
                }
            }

            return createImageArrayFromBytes(fi, imgBytes, width, height);
        } finally {
            if (ras != null)
                ras.close();
        }
    }

    private static ImageArray createImageArrayFromBytes(FileInfo fi, byte[] imgBytes, int width, int height) {
        int spatialSize = width * height;
        int fileType = fi.fileType;
        boolean littleEndian = fi.intelByteOrder;

        if (fileType == FileInfo.RGB) {
            // Chunky RGB: R,G,B,R,G,B,...
            RGBByteImageArray res = new RGBByteImageArray(width, height, 1);
            for (int pi = 0; pi < spatialSize; pi++) {
                int r = imgBytes[3 * pi] & 0xFF;
                int g = imgBytes[3 * pi + 1] & 0xFF;
                int b = imgBytes[3 * pi + 2] & 0xFF;
                res.setPackedIntValAtIndex(pi, (r << 16) | (g << 8) | b);
            }
            return res;
        } else if (fileType == FileInfo.RGB_PLANAR) {
            // Planar RGB: all R, then all G, then all B
            RGBByteImageArray res = new RGBByteImageArray(width, height, 1);
            for (int pi = 0; pi < spatialSize; pi++) {
                int r = imgBytes[pi] & 0xFF;
                int g = imgBytes[spatialSize + pi] & 0xFF;
                int b = imgBytes[2 * spatialSize + pi] & 0xFF;
                res.setPackedIntValAtIndex(pi, (r << 16) | (g << 8) | b);
            }
            return res;
        } else if (fileType == FileInfo.GRAY8) {
            Gray8ImageArray res = new Gray8ImageArray(width, height, 1);
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setChannelIntValAtIndex(pi, 0, imgBytes[pi] & 0xFF);
            }
            return res;
        } else if (fileType == FileInfo.GRAY16_UNSIGNED || fileType == FileInfo.GRAY16_SIGNED) {
            Gray16ImageArray res = new Gray16ImageArray(width, height, 1);
            ByteBuffer buf = ByteBuffer.wrap(imgBytes).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setChannelIntValAtIndex(pi, 0, buf.getShort() & 0xFFFF);
            }
            return res;
        } else if (fileType == FileInfo.GRAY32_FLOAT) {
            FloatImageArray res = new FloatImageArray(width, height, 1);
            ByteBuffer buf = ByteBuffer.wrap(imgBytes).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            for (int pi = 0; pi < spatialSize; pi++) {
                res.setChannelIntValAtIndex(pi, 0, (int) buf.getFloat());
            }
            return res;
        } else {
            throw new IllegalArgumentException("Unsupported TIFF file type: " + fileType);
        }
    }

    private static byte[] readFully(RandomAccessStream ras, int length) throws java.io.IOException {
        byte[] data = new byte[length];
        int read = 0, left = length;
        while (left > 0) {
            int r = ras.read(data, read, left);
            if (r == -1) break;
            read += r;
            left -= r;
        }
        return data;
    }

    private static byte[] lzwUncompress(byte[] input) {
        int[][] table = new int[4096][];
        int tableIndex;
        int bitsToRead = 9;
        int bitPos = 0;
        int clearCode = 256;
        int eoiCode = 257;

        // Initialize table
        for (int i = 0; i < 256; i++) {
            table[i] = new int[]{i};
        }
        tableIndex = 258;

        // Output buffer (estimate: decompress to at most 4x input)
        byte[] output = new byte[input.length * 4];
        int outPos = 0;

        int[] prevEntry = null;

        while (bitPos + bitsToRead <= input.length * 8) {
            int code = readBits(input, bitPos, bitsToRead);
            bitPos += bitsToRead;

            if (code == eoiCode) break;

            if (code == clearCode) {
                // Reset
                bitsToRead = 9;
                tableIndex = 258;
                prevEntry = null;
                continue;
            }

            int[] entry;
            if (code < tableIndex) {
                entry = table[code];
            } else if (code == tableIndex && prevEntry != null) {
                entry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, entry, 0, prevEntry.length);
                entry[prevEntry.length] = prevEntry[0];
            } else {
                break; // Bad code
            }

            // Write entry to output
            if (outPos + entry.length > output.length) {
                byte[] newOutput = new byte[output.length * 2];
                System.arraycopy(output, 0, newOutput, 0, outPos);
                output = newOutput;
            }
            for (int b : entry) {
                output[outPos++] = (byte) b;
            }

            // Add to table
            if (prevEntry != null && tableIndex < 4096) {
                int[] newEntry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, newEntry, 0, prevEntry.length);
                newEntry[prevEntry.length] = entry[0];
                table[tableIndex] = newEntry;
                tableIndex++;
                if (tableIndex == (1 << bitsToRead) && bitsToRead < 12) {
                    bitsToRead++;
                }
            }

            prevEntry = entry;
        }

        byte[] result = new byte[outPos];
        System.arraycopy(output, 0, result, 0, outPos);
        return result;
    }

    private static int readBits(byte[] data, int bitPos, int numBits) {
        int result = 0;
        for (int i = 0; i < numBits; i++) {
            int byteIndex = (bitPos + i) / 8;
            int bitIndex = 7 - ((bitPos + i) % 8); // MSB first
            if (byteIndex < data.length) {
                result = (result << 1) | ((data[byteIndex] >> bitIndex) & 1);
            }
        }
        return result;
    }

    private static void applyHorizontalDifferencing(byte[] data, int bytesPerPixel, int width) {
        int rowBytes = width * bytesPerPixel;
        for (int row = 0; row < data.length / rowBytes; row++) {
            int rowStart = row * rowBytes;
            for (int i = rowStart + bytesPerPixel; i < rowStart + rowBytes; i++) {
                data[i] += data[i - bytesPerPixel];
            }
        }
    }

    private static ImageArray readWithBioFormats(String name, InputStream stream) throws Exception {
        try (IFormatReader reader = new loci.formats.ImageReader()) {
            if (stream != null) {
                byte[] imgBytes = ByteStreams.toByteArray(stream);
                Location.mapFile(name, new ByteArrayHandle(imgBytes));
            }
            reader.setId(name);
            return readImageArrayFromReader(reader);
        }
    }

    private static ImageArray readImageArrayFromReader(IFormatReader reader) throws Exception {
        int imageWidth = reader.getSizeX();
        int imageHeight = reader.getSizeY();
        int imageDepth = reader.getSizeZ();
        int imageChannels = reader.getSizeC();
        int bitsPerPixel = reader.getBitsPerPixel();
        int bytesPerPixel = bitsPerPixel / 8;
        int zSlicePixels = imageWidth * imageHeight;
        byte[] zPlaneBytes = new byte[imageChannels * zSlicePixels * bytesPerPixel];
        WriteableImageArray res;
        if (bitsPerPixel <= 8) {
            if (imageChannels == 1) {
                res = new Gray8ImageArray(imageWidth, imageHeight, imageDepth);
            } else if (imageChannels == 3) {
                res = new RGBByteImageArray(imageWidth, imageHeight, imageDepth);
            } else {
                res = new MultiChannelByteImageArray(imageWidth, imageHeight, imageDepth, imageChannels);
            }
        } else if (bitsPerPixel <= 16) {
            if (imageChannels == 1) {
                res = new Gray16ImageArray(imageWidth, imageHeight, imageDepth);
            } else {
                throw new UnsupportedOperationException("multi-channel 16bits image not supported");
            }
        } else if (bitsPerPixel <= 32) {
            if (imageChannels == 1) {
                res = new FloatImageArray(imageWidth, imageHeight, imageDepth);
            } else {
                throw new UnsupportedOperationException("multi-channel 32bits image not supported");
            }
        } else {
            throw new IllegalArgumentException("Unsupported bitsPerPixel value: " + bitsPerPixel);
        }
        for (int z = 0; z < imageDepth; z++) {
            reader.openBytes(z, zPlaneBytes);
            int zOffset = z * zSlicePixels;
            for (int c = 0; c < imageChannels; c++) {
                ByteBuffer channelZSliceBuffer = ByteBuffer.wrap(
                        zPlaneBytes, c * zSlicePixels * bytesPerPixel, zSlicePixels * bytesPerPixel
                ).order(ByteOrder.BIG_ENDIAN);
                for (int pi = 0; pi < zSlicePixels; pi++) {
                    if (bitsPerPixel <= 8) {
                        int p = channelZSliceBuffer.get() & 0xFF;
                        res.setChannelIntValAtIndex(zOffset + pi, c, p);
                    } else if (bitsPerPixel <= 16) {
                        int p = channelZSliceBuffer.getShort() & 0xFFFF;
                        res.setChannelIntValAtIndex(zOffset + pi, c, p);
                    } else {
                        float p = channelZSliceBuffer.getFloat();
                        res.setChannelIntValAtIndex(zOffset + pi, c, (int) p);
                    }
                }
            }
        }
        return res;
    }

    private static boolean isTiff(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".tif") || lower.endsWith(".tiff");
    }

    private static boolean isStandardImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    private static int packBitsUncompress(byte[] input, byte[] output, int offset, int start, int end) {
        if (end == -1)
            end = Integer.MAX_VALUE;
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

}
