package org.janelia.colormipsearch.imageprocessing;

import java.util.function.IntBinaryOperator;

import org.janelia.colormipsearch.image.AbstractImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.image.view.BinaryMaskImageViewAdapter;
import org.janelia.colormipsearch.image.view.FlippedImageViewAdapter;
import org.janelia.colormipsearch.image.view.HistogramRGBMaxFilterImageViewAdapter;
import org.janelia.colormipsearch.image.view.MaskedImageViewAdapter;
import org.janelia.colormipsearch.image.view.ProxiedImageArrayView;
import org.janelia.colormipsearch.image.view.RGB2Gray8ImageViewAdapter;
import org.janelia.colormipsearch.image.view.SimpleGrayMaxFilterImageViewAdapter;
import org.janelia.colormipsearch.image.view.SimpleRGBMaxFilterImageViewAdapter;
import org.janelia.colormipsearch.image.view.TranslateImageViewAdapter;

public class ImageOperations {

    public static ImageArray combine2(ImageArray img1, ImageArray img2, IntBinaryOperator binaryOperator) {
        return new AbstractImageArray(img1.getWidth(), img1.getHeight(), img1.getDepth(), img1.getChannels()) {

            @Override
            public int getPackedIntValAtIndex(int pi) {
                return binaryOperator.applyAsInt(
                    img1.getPackedIntValAtIndex(pi),
                    img2.getPackedIntValAtIndex(pi)
                );
            }

            @Override
            public int getChannelIntValAtIndex(int pi, int ch) {
                return binaryOperator.applyAsInt(
                    img1.getChannelIntValAtIndex(pi, ch),
                    img2.getChannelIntValAtIndex(pi, ch)
                );
            }
        };
    }

    public static ImageArray combine4(ImageArray img1, ImageArray img2, ImageArray img3, ImageArray img4,
                                      IntQuadOperator quadOperator) {
        return new AbstractImageArray(img1.getWidth(), img1.getHeight(), img1.getDepth(), img1.getChannels()) {

            @Override
            public int getPackedIntValAtIndex(int pi) {
                return quadOperator.applyAsInt(
                        img1.getPackedIntValAtIndex(pi),
                        img2.getPackedIntValAtIndex(pi),
                        img3.getPackedIntValAtIndex(pi),
                        img4.getPackedIntValAtIndex(pi)
                );
            }

            @Override
            public int getChannelIntValAtIndex(int pi, int ch) {
                return quadOperator.applyAsInt(
                        img1.getChannelIntValAtIndex(pi, ch),
                        img2.getChannelIntValAtIndex(pi, ch),
                        img3.getChannelIntValAtIndex(pi, ch),
                        img4.getChannelIntValAtIndex(pi, ch)
                );
            }
        };
    }

    public static ImageArray duplicateImage(ImageArray image, ImageArrayFactory imageFactory) {
        WriteableImageArray newImage = imageFactory.create(image.getWidth(), image.getHeight(), image.getDepth(), image.getChannels());
        for (int pi = 0; pi < image.getSpatialSize(); pi++)
            newImage.setPackedIntValAtIndex(pi, image.getPackedIntValAtIndex(pi));
        return newImage;
    }

    public static ImageArray binaryMask(ImageArray image, int threshold, int foreground) {
        return new ProxiedImageArrayView(
                image,
                new BinaryMaskImageViewAdapter(threshold, foreground, 0)
        );
    }

    public static ImageArray rgbToGray8(ImageArray rgbImage) {
        return new ProxiedImageArrayView(rgbImage, new RGB2Gray8ImageViewAdapter());
    }

    public static ImageArray flipImage(ImageArray image, int axes) {
        return new ProxiedImageArrayView(image, new FlippedImageViewAdapter(axes));
    }

    public static ImageArray maxRGBFilter3D(ImageArray image, int rx, int ry, int rz) {
        return new ProxiedImageArrayView(
                image,
                new SimpleRGBMaxFilterImageViewAdapter(rx, ry, rz));
    }

    public static ImageArray maxRGBFilter2D(ImageArray image, int rx, int ry) {
        return new ProxiedImageArrayView(
                image,
                new HistogramRGBMaxFilterImageViewAdapter(rx, ry, 0));
    }

    public static ImageArray maxGrayFilter3D(ImageArray image, int rx, int ry, int rz) {
        return new ProxiedImageArrayView(
                image,
                new SimpleGrayMaxFilterImageViewAdapter(rx, ry, rz));
    }

    public static ImageArray maskRegion(ImageArray image, ImageMaskPredicate imageMaskPredicate) {
        return new ProxiedImageArrayView(
                image,
                new MaskedImageViewAdapter(imageMaskPredicate, 0)
        );
    }

    public static ImageArray maskRGB(ImageArray image, int threshold) {
        ImageMaskPredicate rgbThresholdPredicate = new ImageMaskPredicate() {
            @Override
            public boolean checkPixelPos(ImageArray imageArray, int x, int y, int z) {
                return false;
            }

            @Override
            public boolean checkPixelVal(int val) {
                int r = (val >> 16) & 0xFF;
                int g = (val >> 8) & 0xFF;
                int b = (val & 0xFF);

                if (r <= threshold && g <= threshold && b <= threshold) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        return maskRegion(image, rgbThresholdPredicate);
    }

    public static ImageArray shift2DImage(ImageArray image, int dx, int dy) {
        return new ProxiedImageArrayView(image, new TranslateImageViewAdapter(dx, dy, 0, 0));
    }

    public static ImageArray shift3DImage(ImageArray image, int dx, int dy, int dz) {
        return new ProxiedImageArrayView(image, new TranslateImageViewAdapter(dx, dy, dz, 0));
    }

    public static int sum(ImageArray imageArray) {
        int s = 0;
        for (int pi = 0; pi < imageArray.getSpatialSize(); pi++) {
            s += imageArray.getPackedIntValAtIndex(pi);
        }
        return s;
    }

    public static int max(ImageArray img) {
        int max = 0;
        for (int pi = 0; pi < img.getSpatialSize(); pi++) {
            int val = img.getPackedIntValAtIndex(pi);
            if (val > max) max = val;
        }
        return max;
    }

    public static int nonZeroCount(ImageArray imageArray) {
        int count = 0;
        for (int pi = 0; pi < imageArray.getSpatialSize(); pi++) {
            if (imageArray.getPackedIntValAtIndex(pi) != 0) count++;
        }
        return count;
    }

}
