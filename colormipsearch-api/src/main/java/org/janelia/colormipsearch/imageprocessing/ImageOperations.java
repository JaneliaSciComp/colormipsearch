package org.janelia.colormipsearch.imageprocessing;

import java.util.function.IntBinaryOperator;

import org.janelia.colormipsearch.image.AbstractImageArray;
import org.janelia.colormipsearch.image.ColorOperations;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.ImageStats;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.image.view.BinaryMaskImageViewAdapter;
import org.janelia.colormipsearch.image.view.ContrastEnhancedImageViewAdapter;
import org.janelia.colormipsearch.image.view.FlippedImageViewAdapter;
import org.janelia.colormipsearch.image.view.HistogramGrayMaxFilterImageViewAdapter;
import org.janelia.colormipsearch.image.view.HistogramRGBMaxFilterImageViewAdapter;
import org.janelia.colormipsearch.image.view.MaskedImageViewAdapter;
import org.janelia.colormipsearch.image.view.ProxiedImageArrayView;
import org.janelia.colormipsearch.image.view.RGB2GrayImageViewAdapter;
import org.janelia.colormipsearch.image.view.RGBConverter;
import org.janelia.colormipsearch.image.view.ScaledIntensityImageViewAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageOperations {

    private static final Logger LOG = LoggerFactory.getLogger(ImageOperations.class);

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
        WriteableImageArray newImage = imageFactory.create(image.getWidth(), image.getHeight(), image.getDepth());
        for (int pi = 0; pi < image.getSpatialSize(); pi++)
            newImage.setPackedIntValAtIndex(pi, image.getPackedIntValAtIndex(pi));
        return newImage;
    }

    public static ImageArray stretchHistogram(ImageArray image, double saturated) {
        ImageStats stats = getImageStatsWithHistogram(image, 65536);
        return new ProxiedImageArrayView(
                image,
                new ContrastEnhancedImageViewAdapter(stats, saturated)
        );
    }

    public static ImageArray scaleIntensity(ImageArray image, double minValue, double maxValue, double scaleFactor, double offset) {
        return new ProxiedImageArrayView(
                image,
                new ScaledIntensityImageViewAdapter(minValue, maxValue, scaleFactor, offset)
        );
    }

    public static ImageArray scaleIntensity(ImageArray image, int sourceMax, int targetMax) {
        return new ProxiedImageArrayView(
                image,
                new ScaledIntensityImageViewAdapter(sourceMax, targetMax)
        );
    }

    public static ImageArray binaryMask(ImageArray image, int threshold, int foreground) {
        return new ProxiedImageArrayView(
                image,
                new BinaryMaskImageViewAdapter(threshold, foreground, 0)
        );
    }

    public static ImageArray rgbToGray(ImageArray rgbImage, RGBConverter rgbConverter) {
        return new ProxiedImageArrayView(rgbImage, new RGB2GrayImageViewAdapter(rgbConverter));
    }

    public static ImageArray rgbToGray8(ImageArray rgbImage) {
        return rgbToGray(rgbImage, ColorOperations::rgb2Gray8);
    }

    public static ImageArray flipImage(ImageArray image, int axes) {
        return axes != 0 ? new ProxiedImageArrayView(image, new FlippedImageViewAdapter(axes)) : image;
    }

    public static ImageArray rgbMaxFilter2D(ImageArray image, int rx, int ry) {
        return new ProxiedImageArrayView(
                image,
                new HistogramRGBMaxFilterImageViewAdapter(rx, ry, 0));
    }

    public static ImageArray gray8MaxFilter3D(ImageArray image, int rx, int ry, int rz) {
        return new ProxiedImageArrayView(
                image,
                new HistogramGrayMaxFilterImageViewAdapter(rx, ry, rz, 8));
    }

    public static ImageArray gray16MaxFilter3D(ImageArray image, int rx, int ry, int rz) {
        return new ProxiedImageArrayView(
                image,
                new HistogramGrayMaxFilterImageViewAdapter(rx, ry, rz, 16));
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

    public static ImageArray maxIntensityProjection(ImageArray imageArray, int minZ, int maxZ, ImageArrayFactory imageArrayFactory) {
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        int sliceSize = width * height;

        int lastZ = maxZ > 0 ? maxZ : depth;

        // Max intensity z-projection
        WriteableImageArray zProjection = imageArrayFactory.create(width, height, 1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int maxVal = 0;
                for (int z = minZ; z < lastZ; z++) {
                    int val = imageArray.getPackedIntValAtIndex(z * sliceSize + y * width + x);
                    if (val > maxVal) maxVal = val;
                }
                zProjection.setPackedIntValAtIndex(y * width + x, maxVal);
            }
        }
        return zProjection;
    }

    /**
     * Count non-background pixels.
     *
     * @param imageArray
     * @return
     */
    public static int countNotBg(ImageArray imageArray) {
        int count = 0;
        for (int pi = 0; pi < imageArray.getSpatialSize(); pi++) {
            if (imageArray.getPackedIntValAtIndex(pi) != 0) count++;
        }
        return count;
    }

    public static ImageStats getImageStats(ImageArray imageArray) {
        long startTime = System.currentTimeMillis();
        ImageStats stats = new ImageStats();
        stats.totalPixels = imageArray.getSpatialSize();
        // Build histogram
        for (int i = 0; i < imageArray.getSpatialSize(); i++) {
            int val = imageArray.getPackedIntValAtIndex(i);
            if (val > 0) {
                stats.nonBgCount++;
                stats.nonBgSum += val;
                if (stats.minVal == 0 || val < stats.minVal) {
                    stats.minVal = val;
                }
                if (val > stats.maxVal) {
                    stats.maxVal = val;
                }
            }
        }
        stats.meanVal = (int) ((double) stats.nonBgSum / stats.nonBgCount);
        LOG.trace("Computed stats in {} secs", (System.currentTimeMillis()-startTime)/1000.);
        return stats;
    }

    public static ImageStats getImageStatsWithHistogram(ImageArray imageArray, int nHistogramBins) {
        long startTime = System.currentTimeMillis();
        ImageStats stats = new ImageStats();
        stats.totalPixels = imageArray.getSpatialSize();
        // Build histogram
        stats.histogram = new int[nHistogramBins];
        for (int i = 0; i < imageArray.getSpatialSize(); i++) {
            int val = imageArray.getPackedIntValAtIndex(i);
            stats.histogram[val]++;
            if (val > 0) {
                stats.nonBgCount++;
                stats.nonBgSum += val;
                if (stats.minVal == 0 || val < stats.minVal) {
                    stats.minVal = val;
                }
                if (val > stats.maxVal) {
                    stats.maxVal = val;
                }
            }
        }
        stats.meanVal = (int) ((double) stats.nonBgSum / stats.nonBgCount);
        LOG.trace("Computed histogram in {} secs", (System.currentTimeMillis()-startTime)/1000.);
        return stats;
    }

    /**
     * Get max pixel value.
     *
     * @param img
     * @return
     */
    public static int max(ImageArray img) {
        int max = 0;
        for (int pi = 0; pi < img.getSpatialSize(); pi++) {
            int val = img.getPackedIntValAtIndex(pi);
            if (val > max)
                max = val;
        }
        return max;
    }

    /**
     * Sum all pixel values.
     *
     * @param imageArray
     * @return
     */
    public static int sum(ImageArray imageArray) {
        int s = 0;
        for (int pi = 0; pi < imageArray.getSpatialSize(); pi++) {
            s += imageArray.getPackedIntValAtIndex(pi);
        }
        return s;
    }

    private static int rgb2Gray(int r, int g, int b, int maxVal) {
        double rw = 1 / 3.;
        double gw = 1 / 3.;
        double bw = 1 / 3.;

        return (int) ((maxVal / (float) maxVal) * (r * rw + g * gw + b * bw + 0.5));
    }

}
