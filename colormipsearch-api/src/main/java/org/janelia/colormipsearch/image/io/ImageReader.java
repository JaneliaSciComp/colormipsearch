package org.janelia.colormipsearch.image.io;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import loci.common.ByteArrayHandle;
import loci.common.Location;
import loci.formats.IFormatReader;
import org.janelia.colormipsearch.image.FloatImageArray;
import org.janelia.colormipsearch.image.Gray8ByteImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.MultiChannelByteImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads images into ImageArray using Bio-Formats.
 */
public class ImageReader {

    private static final Logger LOG = LoggerFactory.getLogger(ImageReader.class);

    public static ImageArray readImageArrayFromStream(String name, InputStream stream) throws Exception {
        try (IFormatReader reader = new loci.formats.ImageReader()) {
            DataInputStream dis = new DataInputStream(stream);
            byte[] imgBytes = dis.readAllBytes();

            Location.mapFile(name, new ByteArrayHandle(imgBytes));
            reader.setId(name);
            return readImageArray(reader);
        }
    }

    public static ImageArray readImageArrayFromFile(String name) {
        try (IFormatReader reader = new loci.formats.ImageReader()) {
            reader.setId(name);
            return readImageArray(reader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ImageArray readImageArray(IFormatReader reader) throws Exception {
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
                res = new Gray8ByteImageArray(imageWidth, imageHeight, imageDepth);
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
                throw new UnsupportedOperationException("multi-channel 16bits image not supported");
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
}
