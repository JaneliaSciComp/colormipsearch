package org.janelia.colormipsearch.cds;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
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
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VolumeSegmentationHelper {

    private static class AlignmentSpaceParams {
        final int width;
        final int height;
        final int depth;

        private AlignmentSpaceParams(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSegmentationHelper.class);
    private static final int[] DILATION_PARAMS = {7, 7, 4};
    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams/*brain*/(
                1210, 566, 174
        ));
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams/*vnc*/(
                573, 1119, 219
        ));
    }};
    private static final int CONNECTED_COMPS_THRESHOLD = 25;
    private static final int CONNECTED_COMPS_MIN_VOLUME = 300;

    private final String alignmentSpace;
    private final AlignmentSpaceParams asParams;
    private final RandomAccessibleInterval<? extends IntegerType<?>> maskVolume;

    public VolumeSegmentationHelper(String alignmentSpace, RandomAccessibleInterval<? extends IntegerType<?>> sourceMaskVolume) {
        this.alignmentSpace = alignmentSpace;
        this.asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("No alignment space parameters were found for " + alignmentSpace);
        }
        this.maskVolume = segmentMaskVolume(sourceMaskVolume);
    }

    /**
     * @param targetVolume target volume file name
     * @param <M>          mask pixel type
     * @param <T>          target pixel type
     * @return
     */
    public <M extends IntegerType<M>, T extends IntegerType<T>>
    RandomAccessibleInterval<? extends RGBPixelType<?>> generateSegmentedCDM(RandomAccessibleInterval<? extends IntegerType<?>> targetVolume) {
        if (maskVolume == null || targetVolume == null) {
            LOG.debug("Mask or target volume is null");
            return null;
        }
        UnsignedShortType intermediatePxType = new UnsignedShortType();

        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<UnsignedShortType> maskedTargetVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp((RandomAccessibleInterval<T>) targetVolume, (RandomAccessibleInterval<M>) maskVolume, intermediatePxType),
                null,
                intermediatePxType
        );
        UnsignedShortType maskedTargetMax = getMax(maskedTargetVolume, intermediatePxType);
        RandomAccessibleInterval<UnsignedShortType> largestMaskedTargetComponent;
        long unflippedVolume;
        LOG.trace("Masked target max value: {}", maskedTargetMax.getInteger());
        if (maskedTargetMax.getInteger() > 25) {
            largestMaskedTargetComponent = findLargestComponent(
                    maskedTargetVolume,
                    intermediatePxType
            );
            unflippedVolume = ImageAccessUtils.fold(largestMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    Long::sum
            );
        } else {
            largestMaskedTargetComponent = maskedTargetVolume;
            unflippedVolume = 0;
        }
        LOG.trace("Unflipped target area: {}", unflippedVolume);
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T> flippedTargetVolume = ImageTransforms.mirrorImage((RandomAccessibleInterval<T>) targetVolume, 0);
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<UnsignedShortType> flippedMaskedTargetVolume = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(flippedTargetVolume, (RandomAccessibleInterval<M>) maskVolume, intermediatePxType),
                null,
                intermediatePxType
        );
        UnsignedShortType flippedMaskedTargetMax = getMax(flippedMaskedTargetVolume, intermediatePxType);
        RandomAccessibleInterval<UnsignedShortType> largestFlippedMaskedTargetComponent;
        long flippedVolume;
        LOG.trace("Flipped masked target max value: {}", flippedMaskedTargetMax.getInteger());
        if (flippedMaskedTargetMax.getInteger() > 25) {
            largestFlippedMaskedTargetComponent = findLargestComponent(
                    flippedMaskedTargetVolume,
                    intermediatePxType
            );
            flippedVolume = ImageAccessUtils.fold(largestFlippedMaskedTargetComponent,
                    0L,
                    (c, p) -> p.getRealDouble() != 0 ? c + 1 : c,
                    Long::sum
            );
        } else {
            largestFlippedMaskedTargetComponent = flippedMaskedTargetVolume;
            flippedVolume = 0;
        }
        LOG.trace("Flipped target area: {}", flippedVolume);

        RandomAccessibleInterval<? extends RGBPixelType<?>> cdm;
        long startCDM = System.currentTimeMillis();
        if (unflippedVolume >= flippedVolume) {
            // generate CDM for unflipped volume
            LOG.trace("Generate CDM from unflipped");
            cdm = CDMGenerationAlgorithm.generateCDM(
                    largestMaskedTargetComponent,
                    intermediatePxType,
                    new ByteArrayRGBPixelType()
            );
        } else {
            // generate CDM for flipped volume
            LOG.trace("Generate CDM from flipped");
            cdm = CDMGenerationAlgorithm.generateCDM(
                    largestFlippedMaskedTargetComponent,
                    intermediatePxType,
                    new ByteArrayRGBPixelType()
            );
        }
        long endCDM = System.currentTimeMillis();
        System.out.printf("Complete CDM in %f secs\n", (endCDM - startCDM) / 1000.);
        return cdm;
    }

    private <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> segmentMaskVolume(RandomAccessibleInterval<? extends IntegerType<?>> sourceImage) {
        if (sourceImage == null) {
            LOG.info("No mask volume was provided");
            return null;
        }
        @SuppressWarnings("unchecked")
        T sourcePxType = (T) sourceImage.randomAccess().get().createVariable();

        RandomAccessibleInterval<? extends IntegerType<?>> contrastEnhancedImage =
                enhanceContrastUsingZProjection(
                        sourceImage,
                        sourcePxType
                );
        long startDilation = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T> prepareDilatedImage = ImageTransforms.dilateImage(
                (RandomAccessibleInterval<T>) contrastEnhancedImage,
                () -> new IntensityPixelHistogram<>(sourcePxType, 16),
                DILATION_PARAMS
        );
        RandomAccessibleInterval<T> dilatedImage = ImageAccessUtils.materializeAsNativeImg(
                prepareDilatedImage, null, sourcePxType
        );
        ImageJFunctions.show(dilatedImage, "Source dilated");
        long endDilation = System.currentTimeMillis();
        System.out.printf("Completed dilation: %f secs\n", (endDilation - startDilation) / 1000.);
        double[] rescaleFactors = new double[]{
                (double) asParams.width/ dilatedImage.dimension(0),
                (double) asParams.height/ dilatedImage.dimension(1),
                (double) asParams.depth / dilatedImage.dimension(2)};
        RandomAccessibleInterval<T> rescaledDilatedImage = ImageTransforms.scaleImage(
                dilatedImage,
                rescaleFactors,
                sourcePxType
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
        T foregroundPx = sourcePxType.createVariable();
        foregroundPx.setInteger(65535);
        RandomAccessibleInterval<T> preBinaryImage = ImageTransforms.maskPixelsMatchingCond(
                rescaledDilatedImage,
                (pos, px) -> {
                    int pxVal = px.getInteger();
                    return pxVal < lowerThreshold || pxVal > upperThreshold;
                },
                foregroundPx
        );
        return ImageAccessUtils.materializeAsNativeImg(preBinaryImage, null, sourcePxType);
    }

    private <S extends IntegerType<S>, T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<S> enhanceContrastUsingZProjection(
            RandomAccessibleInterval<? extends IntegerType<?>> iSourceImage,
            T px) {
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<S> sourceImage = (RandomAccessibleInterval<S>) iSourceImage;
        Comparator<S> sourcePxComparator = Comparator.comparingInt(IntegerType::getInteger);
        RandomAccessibleInterval<S> zProjectionView =
                ImageTransforms.maxIntensityProjection(
                        sourceImage,
                        sourcePxComparator,
                        2, // z-axis
                        sourceImage.min(2),
                        sourceImage.max(2)
                );
        RandomAccessibleInterval<T> zProjection =
                ImageAccessUtils.materializeAsNativeImg(
                        zProjectionView,
                        null,
                        px,
                        (s, t) -> t.setInteger(s.getInteger()));
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
                    () -> sourceImage.randomAccess().get().createVariable()
            );
        } else {
            return sourceImage;
        }
    }

    @SuppressWarnings("unchecked")
    private <S extends IntegerType<S>, T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<T> findLargestComponent(RandomAccessibleInterval<? extends IntegerType<?>> segmentedVolume, T pxType) {
        RandomAccessibleInterval<T> segments = Connect3DComponentsAlgorithm.run(segmentedVolume, CONNECTED_COMPS_THRESHOLD, CONNECTED_COMPS_MIN_VOLUME, new ArrayImgFactory<>(pxType));
        RandomAccessibleInterval<T> largestSegment = ImageTransforms.binarizeImage(segments, 1, 1, pxType);
        return ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.andOp(
                        (RandomAccessibleInterval<S>) segmentedVolume,
                        largestSegment, pxType),
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
