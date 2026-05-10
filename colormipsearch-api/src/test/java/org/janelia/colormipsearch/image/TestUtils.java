package org.janelia.colormipsearch.image;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test utilities for displaying ImageArray images in ImageJ during debugging.
 *
 * Set the system property "display.testImages" to "true" to enable display,
 * e.g. -Ddisplay.testImages=true
 */
public class TestUtils {

    private static final boolean DISPLAY_TEST_IMAGES = true; // Boolean.getBoolean("display.testImages");
    private static final Logger LOG = LoggerFactory.getLogger(TestUtils.class);

    public static int countDiffs(ImageArray img1, ImageArray img2) {
        if (img1.getDepth() != img2.getDepth() ||
                img1.getHeight() != img2.getHeight() ||
                img1.getWidth() != img2.getWidth()) {
            return -1;
        }
        int ndiffs = 0;
        for (int pi = 0; pi < img1.getSpatialSize(); pi++) {
            int p1 = img1.getPackedIntValAtIndex(pi);
            int p2 = img2.getPackedIntValAtIndex(pi);
            if (p1 != p2) ndiffs++;
        }
        return ndiffs;
    }

    public static int countDiffs(ImageArray imageArray, ImageProcessor imageProcessor) {
        if (imageArray.getDepth() != 1 ||
                imageArray.getHeight() != imageProcessor.getHeight() ||
                imageArray.getWidth() != imageProcessor.getWidth()) {
            return -1;
        }
        int ndiffs = 0;
        for (int pi = 0; pi < imageProcessor.getPixelCount(); pi++) {
            if (imageArray.getPackedIntValAtIndex(pi) != imageProcessor.get(pi)) {
                ndiffs++;
            }
        }
        return ndiffs;
    }

    /**
     * Display a 2D or 3D ImageArray in an ImageJ window.
     * For 3D images, each z-slice is added to an ImageStack.
     *
     * @param imageArray the image to display
     * @param title      window title
     */
    public static void displayImage(ImageArray imageArray, String title) {
        if (!DISPLAY_TEST_IMAGES) {
            return;
        }
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        int channels = imageArray.getChannels();
        int slicePixels = width * height;

        if (depth <= 1) {
            // 2D image
            ImageProcessor ip = sliceToIJ1Processor(imageArray, 0, width, height, channels);
            new ImagePlus(title, ip).show();
        } else {
            // 3D image — build an ImageStack
            ImageStack stack = new ImageStack(width, height);
            for (int z = 0; z < depth; z++) {
                ImageProcessor ip = sliceToIJ1Processor(imageArray, z * slicePixels, width, height, channels);
                stack.addSlice("z=" + z, ip);
            }
            new ImagePlus(title, stack).show();
        }
    }

    public static void displayImageProcessor(ImageProcessor ip, String title) {
        if (!DISPLAY_TEST_IMAGES) {
            return;
        }
        new ImagePlus(title, ip).show();
    }

    public static void displayImageJ(ImagePlus ij) {
        if (!DISPLAY_TEST_IMAGES) {
            return;
        }
        ij.show();
    }

    public static ImageArray ij1ProcessorToImageArray(ImageProcessor imageProcessor, ImageArrayFactory imageArrayFactory) {
        WriteableImageArray imageArray = imageArrayFactory.create(imageProcessor.getWidth(), imageProcessor.getHeight(), 1);
        for (int pi = 0; pi < imageProcessor.getPixelCount(); pi++) {
            imageArray.setPackedIntValAtIndex(pi, imageProcessor.get(pi));
        }
        return imageArray;
    }

    public static ImageArray readImageArrayWithIJ1(String fn) {
        ImagePlus ip;
        if (fn.endsWith(".tif") || fn.endsWith(".tiff")) {
            ip = new Opener().openTiff(fn, 1);
            return fromImagePlus(ip);
        } else {
            try {
                return fromImagePlus(new ImagePlus(fn, ImageIO.read(new File(fn))));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Read an image array from an ImageJ ImagePlus object.
     *
     * @param imagePlus
     * @return
     */
    private static ImageArray fromImagePlus(ImagePlus imagePlus) {
        int type = imagePlus.getType();
        ImageProcessor ip = imagePlus.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        switch (type) {
            case ImagePlus.GRAY8: {
                LOG.debug("Read {} GRAY8 {}x{} pixels", ((byte[]) ip.getPixels()).length, width, height);
                byte[] srcPixels = (byte[]) ip.getPixels();
                Gray8ImageArray result = new Gray8ImageArray(width, height, 1);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setPackedIntValAtIndex(pi, srcPixels[pi] & 0xFF);
                }
                return result;
            }
            case ImagePlus.GRAY16: {
                LOG.debug("Read {} GRAY16 {}x{} pixels", ((short[]) ip.getPixels()).length, width, height);
                short[] srcPixels = (short[]) ip.getPixels();
                Gray16ImageArray result = new Gray16ImageArray(width, height, 1);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setPackedIntValAtIndex(pi, srcPixels[pi] & 0xFFFF);
                }
                return result;
            }
            case ImagePlus.COLOR_RGB: {
                LOG.debug("Read {} RGB {}x{} pixels", ((int[]) ip.getPixels()).length, width, height);
                int[] srcPixels = (int[]) ip.getPixels();
                RGBByteImageArray result = new RGBByteImageArray(width, height, 1);
                for (int pi = 0; pi < width * height; pi++) {
                    result.setPackedIntValAtIndex(pi, srcPixels[pi]); // setIntVal unpacks channels from packed int
                }
                return result;
            }
            default:
                throw new IllegalArgumentException("Unsupported image type: " + type);
        }
    }

    public static ImageProcessor sliceToIJ1Processor(ImageArray imageArray, int offset, int width, int height, int channels) {
        int slicePixels = width * height;
        if (imageArray instanceof Gray16ImageArray) {
            short[] pixels = new short[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = (short) (imageArray.getPackedIntValAtIndex(offset + pi) & 0xFFFF);
            }
            return new ShortProcessor(width, height, pixels, null);
        } else if (channels >= 3) {
            int[] pixels = new int[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = imageArray.getPackedIntValAtIndex(offset + pi);
            }
            return new ColorProcessor(width, height, pixels);
        } else {
            byte[] pixels = new byte[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = (byte) (imageArray.getPackedIntValAtIndex(offset + pi) & 0xFF);
            }
            return new ByteProcessor(width, height, pixels);
        }
    }

    public static void saveImageAsPng(ImageArray imageArray, String filename) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        int channels = imageArray.getChannels();
        int slicePixels = width * height;

        ImagePlus ipImage;
        if (depth <= 1) {
            // 2D image
            ImageProcessor ip = sliceToIJ1Processor(imageArray, 0, width, height, channels);
            ipImage = new ImagePlus(null, ip);
        } else {
            // 3D image — build an ImageStack
            ImageStack stack = new ImageStack(width, height);
            for (int z = 0; z < depth; z++) {
                ImageProcessor ip = sliceToIJ1Processor(imageArray, z * slicePixels, width, height, channels);
                stack.addSlice("z=" + z, ip);
            }
            ipImage = new ImagePlus(null, stack);
        }
        new FileSaver(ipImage).saveAsPng(filename);
    }

    /**
     * Wait for a key press on System.in.
     * Use this in a debugger to keep displayed images visible before the test terminates.
     */
    public static void waitForKey() {
        if (DISPLAY_TEST_IMAGES) {
            try {
                System.in.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
