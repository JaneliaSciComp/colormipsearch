package org.janelia.colormipsearch.cds;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.IntensityPixelHistogram;
import org.janelia.colormipsearch.image.algorithms.CDMGenerationAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Connect3DComponentsAlgorithm;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class VolumeSegmentationHelper {
    private static class AlignmentSpaceParams {
        final int width;
        final int height;
        final int depth;
        final double xyScaling;
        final double zScaling;
        final double maskScale;
        final int maskRadius;
        final int targetRadius;

        private AlignmentSpaceParams(int width, int height, int depth,
                                     double xyScaling, double zScaling) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.xyScaling = xyScaling;
            this.zScaling = zScaling;
            this.maskScale = 0.5;
            this.maskRadius = 1;
            this.targetRadius = 20;
        }
    }

    private static final int[] DILATION_PARAMS = {7, 7, 4};
    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams/*brain*/(
                1210, 566, 174,
                0.5189161, 1.0
        ));
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams/*vnc*/(
                573, 1119, 219,
                0.4611220, 0.7
        ));
    }};
    private static final int CONNECTED_COMPS_THRESHOLD = 25;
    private static final int CONNECTED_COMPS_MIN_VOLUME = 300;

    public static RandomAccessibleInterval<? extends IntegerType<?>> readLMMaskVolume(String fn) {
        RandomAccessibleInterval<UnsignedShortType> sourceImage = ImageReader.readImage(fn, new UnsignedShortType());
        return ImageAccessUtils.materializeAsNativeImg(Views.invertAxis(sourceImage, 0), null, new UnsignedShortType());
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> readLMTargeVolume(String fn) {
        return ImageReader.readImage(fn, new UnsignedShortType());
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> readEMMaskVolume(String fn, String alignmentSpace) {
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
                    (int) (asParams.width * asParams.maskScale),
                    (int) (asParams.height * asParams.maskScale),
                    (int) (asParams.depth * asParams.maskScale),
                    asParams.xyScaling / asParams.maskScale,
                    asParams.zScaling / asParams.maskScale,
                    asParams.maskRadius,
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
        System.out.printf("Completed dilation: %f secs\n", (endDilation - startDilation) / 1000.);
        double[] rescaleFactors = new double[]{1 / asParams.maskScale, 1 / asParams.maskScale, 1 / asParams.maskScale};
        RandomAccessibleInterval<UnsignedShortType> rescaledDilatedImage = ImageTransforms.scaleImage(
                dilatedImage,
                rescaleFactors,
                new UnsignedShortType()
        );

        long endRescale = System.currentTimeMillis();
        System.out.printf("Completed rescale: %f secs\n", (endRescale - endDilation) / 1000.);
        Cursor<UnsignedShortType> maxCur = Max.findMax(Views.flatIterable(rescaledDilatedImage));
        int maxValue = maxCur.get().getInteger();
        int lowerThreshold, upperThreshold;
        if (maxValue > 2000) {
            lowerThreshold = 2000;
            upperThreshold = 65535;
        } else {
            lowerThreshold = 1;
            upperThreshold = 65535;
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
        RandomAccessibleInterval<T> zProjection = ImageTransforms.maxIntensityProjection(
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
                    px::createVariable
            );
        } else {
            return sourceImage;
        }
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> readEMTargetVolume(String fn, String alignmentSpace) {
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
                    asParams.width, asParams.width, asParams.depth,
                    asParams.xyScaling, asParams.zScaling,
                    asParams.targetRadius,
                    new UnsignedShortType(255)
            );
        }
        return sourceImage;
    }

    public static <S extends IntegerType<S>> RandomAccessibleInterval<IntRGBPixelType> generateSegmentedCDM(RandomAccessibleInterval<S> maskVolume,
                                                                                                            RandomAccessibleInterval<S> targetVolume) {
        UnsignedIntType targetMaskedPxType = new UnsignedIntType();
        RandomAccessibleInterval<UnsignedIntType> targetMaskedVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(targetVolume, maskVolume, targetMaskedPxType),
                null,
                targetMaskedPxType
        );
        UnsignedIntType targetMaskedMax = getMax(targetMaskedVolume, targetMaskedPxType);
        RandomAccessibleInterval<UnsignedIntType> largestUnflippedMaskedTargetComponent;
        long unflippedVolume;
        if (targetMaskedMax.getInt() > 25) {
            largestUnflippedMaskedTargetComponent = findLargestComponent(
                    targetMaskedVolume,
                    targetMaskedPxType
            );
            unflippedVolume = ImageAccessUtils.fold(largestUnflippedMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    (c1, c2) -> c1 + c2
            );
        } else {
            largestUnflippedMaskedTargetComponent = targetMaskedVolume;
            unflippedVolume = 0;
        }

        RandomAccessibleInterval<S> flippedTargetVolume = ImageTransforms.mirrorImage(targetVolume, 0);
        RandomAccessibleInterval<UnsignedIntType> flippedTargetMaskedVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(flippedTargetVolume, maskVolume, targetMaskedPxType),
                null,
                targetMaskedPxType
        );

        UnsignedIntType flippedTargetMaskedMax = getMax(targetMaskedVolume, targetMaskedPxType);
        RandomAccessibleInterval<UnsignedIntType> largestFlippedMaskedTargetComponent;
        long flippedVolume;
        if (flippedTargetMaskedMax.getInt() > 25) {
            largestFlippedMaskedTargetComponent = findLargestComponent(
                    flippedTargetMaskedVolume,
                    targetMaskedPxType
            );
            flippedVolume = ImageAccessUtils.fold(largestFlippedMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    (c1, c2) -> c1 + c2
            );
        } else {
            largestFlippedMaskedTargetComponent = flippedTargetMaskedVolume;
            flippedVolume = 0;
        }

        if (unflippedVolume >= flippedVolume) {
            // generate CDM for unflipped volume
            return CDMGenerationAlgorithm.generateCDM(
                    largestUnflippedMaskedTargetComponent,
                    targetMaskedPxType,
                    new IntRGBPixelType()
            );
        } else {
            // generate CDM for flipped volume
            return CDMGenerationAlgorithm.generateCDM(
                    largestFlippedMaskedTargetComponent,
                    targetMaskedPxType,
                    new IntRGBPixelType()
            );
        }
    }

    private static <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> findLargestComponent(RandomAccessibleInterval<T> segmentedVolume, T pxType) {
        RandomAccessibleInterval<T> segments = Connect3DComponentsAlgorithm.run(segmentedVolume, CONNECTED_COMPS_THRESHOLD, CONNECTED_COMPS_MIN_VOLUME, new ArrayImgFactory<>(pxType));
        RandomAccessibleInterval<T> largestSegment = ImageTransforms.binarizeImage(segments, 1, 1, pxType);
        return ImageTransforms.andOp(segmentedVolume, largestSegment, pxType);
    }

    private static <T extends Type<T> & Comparable<T>> T getMax(RandomAccessibleInterval<T> img, T pxType) {
        T minPx = pxType.createVariable();
        T maxPx = pxType.createVariable();
        ComputeMinMax.computeMinMax(img, minPx, maxPx);
        return maxPx;
    }
}
