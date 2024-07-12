package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.Views;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageAccess;
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

    private static long[] DILATION_PARAMS = {7, 7, 4};
    private static Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
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
        ImageAccess<UnsignedIntType> sourceImage = ImageReader.readImage(fn, new UnsignedIntType());
        return ImageAccessUtils.materializeAsNativeImg(Views.invertAxis(sourceImage, 0), null, new UnsignedIntType());
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> prepareEMSegmentedVolume(String fn, String alignmentSpace) {
        ImageAccess<UnsignedIntType> sourceImage;

        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters were found for " + alignmentSpace);
        }
        if (StringUtils.endsWithIgnoreCase(fn, ".nrrd")) {
            sourceImage = ImageReader.readImage(fn, new UnsignedIntType());
        } else {
            // default to .swc
            sourceImage = ImageReader.readSWC(fn,
                    asParams.drawWidth, asParams.drawHeight, asParams.drawDepth,
                    asParams.xyScaling, asParams.zScaling, asParams.radius,
                    new UnsignedIntType(255)
            );
        }
        System.out.println("!!!! DONE READ");
        ImageAccess<UnsignedIntType> contrastEnhancedImage = enhanceContrastUsingZProjection(
                sourceImage, UnsignedIntType::compareTo
        );
        System.out.println("!!!! DONE ENHANCE " + Arrays.toString(contrastEnhancedImage.dimensionsAsLongArray()));
        long beforeDilation = System.currentTimeMillis();
        ImageAccess<UnsignedIntType> prepareDilatedImage = ImageTransforms.dilateImage(
                contrastEnhancedImage,
                () -> new IntensityPixelHistogram<>(new UnsignedIntType()),
                DILATION_PARAMS
        );
        ImageAccess<UnsignedIntType> dilatedImage = ImageAccessUtils.materialize(prepareDilatedImage, null);
        long afterDilation = System.currentTimeMillis();
        System.out.println("!!!! DONE DILATE " + Arrays.toString(contrastEnhancedImage.dimensionsAsLongArray()) +
                (afterDilation-beforeDilation)/1000. + " secs");
        ImageAccess<UnsignedIntType> rescaledDilatedContrastEnhancedImage = ImageTransforms.scaleImage(
                dilatedImage,
                asParams.rescaleFactors()
        );
        return rescaledDilatedContrastEnhancedImage; // !!!!!!! FIXME
    }

    private static <T extends IntegerType<T> & NativeType<T>> ImageAccess<T> enhanceContrastUsingZProjection(ImageAccess<T> sourceImage,
                                                                                                             Comparator<T> sourcePxComparator) {
        ImageAccess<T> zProjection = ImageTransforms.createMIP(
                sourceImage,
                sourcePxComparator,
                2, // z-axis
                sourceImage.min(2),
                sourceImage.max(2)
        );
        ImageAccess<T> enhancedZProjection = ImageTransforms.enhanceContrast(
                zProjection, 0.35, -1, -1, 65536
        );
        T minPx = enhancedZProjection.getBackgroundValue().createVariable();
        T maxPx = enhancedZProjection.getBackgroundValue().createVariable();
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
                    sourceImage.getBackgroundValue().createVariable()
            );
        } else {
            return sourceImage;
        }
    }
}
