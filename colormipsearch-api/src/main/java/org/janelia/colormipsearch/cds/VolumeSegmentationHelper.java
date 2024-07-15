package org.janelia.colormipsearch.cds;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.IntensityPixelHistogram;
import org.janelia.colormipsearch.image.io.ImageReader;

public class VolumeSegmentationHelper {
    private static class AlignmentSpaceParams {
        final int drawWidth;
        final int drawHeight;
        final int drawDepth;
        final int finalWidth;
        final int finalHeight;
        final int finalDepth;
        final double xyScaling;
        final double zScaling;
        final int radius;

        private AlignmentSpaceParams(int drawWidth, int drawHeight, int drawDepth,
                                     int finalWidth, int finalHeight, int finalDepth,
                                     double xyScaling, double zScaling) {
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
            this.drawDepth = drawDepth;
            this.finalWidth = finalWidth;
            this.finalHeight = finalHeight;
            this.finalDepth = finalDepth;
            this.xyScaling = xyScaling;
            this.zScaling = zScaling;
            this.radius = 1;
        }

        double[] rescaleFactors() {
            return new double[] {
                    (double) finalWidth/ drawWidth,
                    (double) finalHeight/ drawHeight,
                    (double) finalDepth/ drawDepth,
            };
        }
    }

    private static final int[] DILATION_PARAMS = {7, 7, 4};
    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams/*brain*/(
                685, 283, 87,
                1210, 566, 174,
                1.0378322, 2.0
        ));
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams/*vnc*/(
                287, 560, 110,
                573, 1119, 219,
                0.922244, 1.4
        ));
    }};

    public static RandomAccessibleInterval<? extends IntegerType<?>> prepareLMSegmentedVolume(String fn) {
        RandomAccessibleInterval<UnsignedShortType> sourceImage = ImageReader.readImage(fn, new UnsignedShortType());
        return ImageAccessUtils.materializeAsNativeImg(Views.invertAxis(sourceImage, 0), null, new UnsignedShortType());
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> prepareEMSegmentedVolume(String fn, String alignmentSpace) {
        RandomAccessibleInterval<UnsignedShortType> sourceImage;

        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters were found for " + alignmentSpace);
        }
        if (StringUtils.endsWithIgnoreCase(fn, ".nrrd")) {
            sourceImage = ImageReader.readImage(fn, new UnsignedShortType());
        } else {
            // default to .swc
            sourceImage = ImageReader.readSWC(fn,
                    asParams.drawWidth, asParams.drawHeight, asParams.drawDepth,
                    asParams.xyScaling, asParams.zScaling, asParams.radius,
                    new UnsignedShortType(255)
            );
        }
        RandomAccessibleInterval<UnsignedShortType> contrastEnhancedImage = enhanceContrastUsingZProjection(
                sourceImage, UnsignedShortType::compareTo, new UnsignedShortType()
        );
        long startDilation = System.currentTimeMillis();
        RandomAccessibleInterval<UnsignedShortType> prepareDilatedImage = ImageTransforms.dilateImage(
                contrastEnhancedImage,
                () -> new IntensityPixelHistogram<>(new UnsignedShortType()),
                DILATION_PARAMS
        );
        RandomAccessibleInterval<UnsignedShortType> dilatedImage = ImageAccessUtils.materializeAsNativeImg(prepareDilatedImage, null, new UnsignedShortType(0));
        long endDilation = System.currentTimeMillis();
        System.out.printf("Completed dilation: %f secs\n", (endDilation-startDilation)/1000.);
        RandomAccessibleInterval<UnsignedShortType> preRescaledDilatedImage = ImageTransforms.scaleImage(
                dilatedImage,
                asParams.rescaleFactors()
        );
        RandomAccessibleInterval<UnsignedShortType> rescaledDilatedImage = ImageAccessUtils.materializeAsNativeImg(preRescaledDilatedImage, null, new UnsignedShortType());
        long endRescale = System.currentTimeMillis();
        System.out.printf("Completed rescale: %f secs\n", (endRescale-endDilation)/1000.);
        Cursor<UnsignedShortType> maxCur = Max.findMax(Views.flatIterable(rescaledDilatedImage));
        int maxValue = maxCur.get().getInteger();
        int lowerThreshold, upperThreshold;
        if (maxValue > 2000) {
            lowerThreshold = 2000; upperThreshold = 65535;
        } else {
            lowerThreshold = 1; upperThreshold = 65535;
        }
        RandomAccessibleInterval<UnsignedShortType> preBinaryImage = ImageTransforms.maskPixelsMatchingCond(
                rescaledDilatedImage,
                (pos, px) -> {
                    int pxVal = px.getInteger();
                    return pxVal < lowerThreshold || pxVal > upperThreshold;
                },
                new UnsignedShortType(65535)
        );
        return ImageAccessUtils.materializeAsNativeImg(preBinaryImage, null, new UnsignedShortType());
    }

    private static <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> enhanceContrastUsingZProjection(RandomAccessibleInterval<T> sourceImage,
                                                                                                                          Comparator<T> sourcePxComparator,
                                                                                                                          T px) {
        RandomAccessibleInterval<T> zProjection = ImageTransforms.createMIP(
                sourceImage,
                sourcePxComparator,
                2, // z-axis
                sourceImage.min(2),
                sourceImage.max(2)
        );
        RandomAccessibleInterval<T> enhancedZProjection = ImageTransforms.enhanceContrast(
                zProjection, () -> px, 0.35, -1, -1, 65536
        );
        T minPx = px.createVariable();
        T maxPx = px.createVariable();
        ComputeMinMax.computeMinMax(enhancedZProjection, minPx, maxPx);
        if (maxPx.getRealDouble() != 255) {
            double newMin = 0.0;
            double newMax = 255.0;
            double scale = (newMax - newMin) / (maxPx.getRealDouble() - minPx.getRealDouble());
            double offset = newMin - minPx.getRealDouble() * scale;
            return ImageTransforms.createPixelTransformation(
                    sourceImage,
                    (s, t) -> {
                        t.setReal(s.getRealDouble() * scale + offset);
                    },
                    () -> px.createVariable()
            );
        } else {
            return sourceImage;
        }
    }
}
