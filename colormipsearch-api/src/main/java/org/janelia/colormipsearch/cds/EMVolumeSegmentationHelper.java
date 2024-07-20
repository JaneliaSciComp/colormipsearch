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
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.IntensityPixelHistogram;
import org.janelia.colormipsearch.image.algorithms.CDMGenerationAlgorithm;
import org.janelia.colormipsearch.image.algorithms.Connect3DComponentsAlgorithm;
import org.janelia.colormipsearch.image.algorithms.PixelIntensityAlgorithms;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;

public class EMVolumeSegmentationHelper {
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

    private final String maskVolumeFn;
    private final String alignmentSpace;
    private final RandomAccessibleInterval<? extends IntegerType<?>> maskVolume;

    public EMVolumeSegmentationHelper(String maskVolumeFn, String alignmentSpace) {
        this.maskVolumeFn = maskVolumeFn;
        this.alignmentSpace = alignmentSpace;
        this.maskVolume = initializeEMMaskVolume(new UnsignedShortType());
    }

    /**
     *
     * @param targetVolumeFn target volume file name
     * @param targetPxType target pixel type
     * @return
     * @param <M> mask pixel type
     * @param <T> target pixel type
     */
    public <M extends IntegerType<M>, T extends IntegerType<T> & NativeType<T>>
    RandomAccessibleInterval<IntRGBPixelType> generateSegmentedCDM(String targetVolumeFn, T targetPxType) {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters were found for " + alignmentSpace);
        }

        RandomAccessibleInterval<T> targetVolume = readTargetVolume(targetVolumeFn, asParams, targetPxType);
        System.out.printf("Read Target volume: (%d, %d, %d) from %s\n",
                targetVolume.dimension(0),
                targetVolume.dimension(1),
                targetVolume.dimension(2),
                targetVolumeFn);
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T> maskedTargetVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(targetVolume, (RandomAccessibleInterval<M>) maskVolume, targetPxType),
                null,
                targetPxType
        );
        T maskedTargetMax = getMax(maskedTargetVolume, targetPxType);
        RandomAccessibleInterval<T> largestMaskedTargetComponent;
        long unflippedVolume;
        System.out.printf("Masked target max value: %d\n", maskedTargetMax.getInteger());
        if (maskedTargetMax.getInteger() > 25) {
            largestMaskedTargetComponent = findLargestComponent(
                    maskedTargetVolume,
                    targetPxType
            );
            unflippedVolume = ImageAccessUtils.fold(largestMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    (c1, c2) -> c1 + c2
            );
        } else {
            largestMaskedTargetComponent = maskedTargetVolume;
            unflippedVolume = 0;
        }
        System.out.printf("Unflipped volume: %d\n", unflippedVolume);
        RandomAccessibleInterval<T> flippedTargetVolume = ImageTransforms.mirrorImage(targetVolume, 0);
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T> flippedMaskedTargetVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(flippedTargetVolume, (RandomAccessibleInterval<M>) maskVolume, targetPxType),
                null,
                targetPxType
        );
        T flippedMaskedTargetMax = getMax(flippedMaskedTargetVolume, targetPxType);
        RandomAccessibleInterval<T> largestFlippedMaskedTargetComponent;
        long flippedVolume;
        System.out.printf("Flipped masked target max value: %d\n", flippedMaskedTargetMax.getInteger());
        if (flippedMaskedTargetMax.getInteger() > 25) {
            largestFlippedMaskedTargetComponent = findLargestComponent(
                    flippedMaskedTargetVolume,
                    targetPxType
            );
            flippedVolume = ImageAccessUtils.fold(largestFlippedMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    (c1, c2) -> c1 + c2
            );
        } else {
            largestFlippedMaskedTargetComponent = flippedMaskedTargetVolume;
            flippedVolume = 0;
        }

        RandomAccessibleInterval<IntRGBPixelType> cdm;
        long startCDM = System.currentTimeMillis();
        if (unflippedVolume >= flippedVolume) {
            // generate CDM for unflipped volume
            System.out.println("Generate CDM from unflipped");
            cdm = CDMGenerationAlgorithm.generateCDM(
                    largestMaskedTargetComponent,
                    targetPxType,
                    new IntRGBPixelType()
            );
        } else {
            // generate CDM for flipped volume
            System.out.println("Generate CDM from flipped");
            cdm = CDMGenerationAlgorithm.generateCDM(
                    largestFlippedMaskedTargetComponent,
                    targetPxType,
                    new IntRGBPixelType()
            );
        }
        long endCDM = System.currentTimeMillis();
        System.out.printf("Complete CDM in %f secs\n", (endCDM - startCDM) / 1000.);
        return cdm;
    }


    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> initializeEMMaskVolume(T pxType) {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters were found for " + alignmentSpace);
        }
        RandomAccessibleInterval<T> sourceVolume = readMaskVolume(maskVolumeFn, asParams, pxType);
        System.out.printf("Read mask volume: (%d, %d, %d) from %s\n",
                sourceVolume.dimension(0),
                sourceVolume.dimension(1),
                sourceVolume.dimension(2),
                maskVolumeFn);
        return segmentMaskVolume(sourceVolume, asParams, pxType);
    }

    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> segmentMaskVolume(RandomAccessibleInterval<T> sourceImage,
                                                                                                     AlignmentSpaceParams asParams,
                                                                                                     T maskPxType) {
        Comparator<T> pxComparator = Comparator.comparingInt(IntegerType::getInteger);
        RandomAccessibleInterval<T> contrastEnhancedImage = enhanceContrastUsingZProjection(
                sourceImage, pxComparator, maskPxType
        );
        long startDilation = System.currentTimeMillis();
        RandomAccessibleInterval<T> prepareDilatedImage = ImageTransforms.dilateImage(
                contrastEnhancedImage,
                () -> new IntensityPixelHistogram<>(maskPxType, 16),
                DILATION_PARAMS
        );
        RandomAccessibleInterval<T> dilatedImage = ImageAccessUtils.materializeAsNativeImg(
                prepareDilatedImage, null, maskPxType
        );
        long endDilation = System.currentTimeMillis();
        System.out.printf("Completed dilation: %f secs\n", (endDilation - startDilation) / 1000.);
        double[] rescaleFactors = new double[]{1 / asParams.maskScale, 1 / asParams.maskScale, 1 / asParams.maskScale};
        RandomAccessibleInterval<T> rescaledDilatedImage = ImageTransforms.scaleImage(
                dilatedImage,
                rescaleFactors,
                maskPxType
        );

        long endRescale = System.currentTimeMillis();
        System.out.printf("Completed rescale: %f secs\n", (endRescale - endDilation) / 1000.);
        Cursor<T> maxCur = Max.findMax(Views.flatIterable(rescaledDilatedImage));
        int maxValue = maxCur.get().getInteger();
        int lowerThreshold, upperThreshold;
        if (maxValue > 2000) {
            lowerThreshold = 2000;
            upperThreshold = 65535;
        } else {
            lowerThreshold = 1;
            upperThreshold = 65535;
        }
        T foregroundPx = maskPxType.createVariable();
        foregroundPx.setInteger(65535);
        RandomAccessibleInterval<T> preBinaryImage = ImageTransforms.maskPixelsMatchingCond(
                rescaledDilatedImage,
                (pos, px) -> {
                    int pxVal = px.getInteger();
                    return pxVal < lowerThreshold || pxVal > upperThreshold;
                },
                foregroundPx
        );
        return ImageAccessUtils.materializeAsNativeImg(preBinaryImage, null, maskPxType);
    }


    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> enhanceContrastUsingZProjection(RandomAccessibleInterval<T> sourceImage,
                                                                                                                   Comparator<T> sourcePxComparator,
                                                                                                                   T px) {
        RandomAccessibleInterval<T> zProjection = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.maxIntensityProjection(
                        sourceImage,
                        sourcePxComparator,
                        2, // z-axis
                        sourceImage.min(2),
                        sourceImage.max(2)
                ),
                null,
                px);
        PixelIntensityAlgorithms.stretchHistogram(zProjection, 0.35, -1, -1, 65536);
        T minPx = px.createVariable();
        T maxPx = px.createVariable();
        ComputeMinMax.computeMinMax(zProjection, minPx, maxPx);
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

    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> readMaskVolume(String fn, AlignmentSpaceParams asParams, T pxType) {
        if (StringUtils.endsWithIgnoreCase(fn, ".nrrd")) {
            // read the NRRD and scale it down
            return ImageTransforms.scaleImage(
                    ImageReader.readImage(fn, pxType),
                    new double[]{asParams.maskScale, asParams.maskScale, asParams.maskScale},
                    pxType
            );
        } else {
            // default to .swc
            T foreground = pxType.createVariable();
            foreground.setInteger(255);
            return ImageReader.readSWC(fn,
                    (int) (asParams.width * asParams.maskScale),
                    (int) (asParams.height * asParams.maskScale),
                    (int) (asParams.depth * asParams.maskScale),
                    asParams.xyScaling / asParams.maskScale,
                    asParams.xyScaling / asParams.maskScale,
                    asParams.zScaling / asParams.maskScale,
                    asParams.maskRadius,
                    pxType
            );
        }
    }

    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> readTargetVolume(String fn, AlignmentSpaceParams asParams, T pxType) {
        if (StringUtils.endsWithIgnoreCase(fn, ".nrrd")) {
            return ImageReader.readImage(fn, pxType);
        } else {
            // default to .swc
            T foreground = pxType.createVariable();
            foreground.setInteger(255);
            return ImageReader.readSWC(fn,
                    asParams.width, asParams.height, asParams.depth,
                    asParams.xyScaling, asParams.xyScaling, asParams.zScaling,
                    asParams.targetRadius,
                    pxType
            );
        }
    }

    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> findLargestComponent(RandomAccessibleInterval<T> segmentedVolume, T pxType) {
        RandomAccessibleInterval<T> segments = Connect3DComponentsAlgorithm.run(segmentedVolume, CONNECTED_COMPS_THRESHOLD, CONNECTED_COMPS_MIN_VOLUME, new ArrayImgFactory<>(pxType));
        RandomAccessibleInterval<T> largestSegment = ImageTransforms.binarizeImage(segments, 1, 1, pxType);
        return ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(segmentedVolume, largestSegment, pxType),
                null,
                pxType
        );
    }

    private <T extends Type<T> & IntegerType<T>> T getMax(RandomAccessibleInterval<T> img, T pxType) {
        T minPx = pxType.createVariable();
        T maxPx = pxType.createVariable();
        ComputeMinMax.computeMinMax(img, minPx, maxPx);
        System.out.printf("MIN/MAX in getMax: %d, %d\n", minPx.getInteger(), maxPx.getInteger());
        return maxPx;
    }
}
