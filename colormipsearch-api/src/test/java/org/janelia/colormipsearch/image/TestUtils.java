package org.janelia.colormipsearch.image;

import java.io.IOException;
import java.io.UncheckedIOException;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Test utilities for displaying ImageArray images in ImageJ during debugging.
 *
 * Set the system property "display.testImages" to "true" to enable display,
 * e.g. -Ddisplay.testImages=true
 */
public class TestUtils {

    private static final boolean DISPLAY_TEST_IMAGES = true; //Boolean.getBoolean("display.testImages");

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
            ImageProcessor ip = sliceToProcessor(imageArray, 0, width, height, channels);
            new ImagePlus(title, ip).show();
        } else {
            // 3D image — build an ImageStack
            ImageStack stack = new ImageStack(width, height);
            for (int z = 0; z < depth; z++) {
                ImageProcessor ip = sliceToProcessor(imageArray, z * slicePixels, width, height, channels);
                stack.addSlice("z=" + z, ip);
            }
            new ImagePlus(title, stack).show();
        }
    }

    /**
     * Display a 2D or 3D ImageArray and wait for a key press.
     * Convenience method combining displayImage + waitForKey.
     */
    public static void displayImageAndWait(ImageArray imageArray, String title) {
        displayImage(imageArray, title);
        waitForKey();
    }

    private static ImageProcessor sliceToProcessor(ImageArray imageArray, int offset, int width, int height, int channels) {
        int slicePixels = width * height;
        if (imageArray instanceof ShortImageArray) {
            short[] pixels = new short[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = (short) (imageArray.get(offset + pi) & 0xFFFF);
            }
            return new ShortProcessor(width, height, pixels, null);
        } else if (channels >= 3) {
            int[] pixels = new int[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = imageArray.get(offset + pi);
            }
            return new ColorProcessor(width, height, pixels);
        } else {
            byte[] pixels = new byte[slicePixels];
            for (int pi = 0; pi < slicePixels; pi++) {
                pixels[pi] = (byte) (imageArray.get(offset + pi) & 0xFF);
            }
            return new ByteProcessor(width, height, pixels);
        }
    }
}
