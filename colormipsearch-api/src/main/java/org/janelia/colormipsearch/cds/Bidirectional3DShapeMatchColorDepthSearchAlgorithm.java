package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithm.class);

    private static final int GAP_THRESHOLD = 3;

    private final RandomAccessibleInterval<? extends RGBPixelType<?>> queryImageAccess;
    private final RandomAccessibleInterval<UnsignedShortType> queryGradientImg;
    private final RandomAccessibleInterval<UnsignedByteType> querySignal;
    private final VolumeSegmentationHelper volumeSegmentationHelper;

    @SuppressWarnings("unchecked")
    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> sourceQueryImage,
                                                       Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers,
                                                       BiPredicate<long[], ? extends IntegerType<?>> excludedRegionCondition,
                                                       RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
                                                       ExecutorService executorService,
                                                       String alignmentSpace,
                                                       int queryThreshold,
                                                       int targetThreshold,
                                                       boolean withMirrorFlag) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        this.queryImageAccess = applyRGBThreshold(
                (RandomAccessibleInterval<? extends RGBPixelType<?>>) applyMaskCond(sourceQueryImage, (BiPredicate<long[], RGBPixelType<?>>)excludedRegionCondition),
                queryThreshold
        );
        this.queryGradientImg = DistanceTransformAlgorithm.generateDistanceTransform(
                queryImageAccess,
                5);

        this.querySignal = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.rgbToSignalTransformation(queryImageAccess, queryThreshold),
                null,
                new UnsignedByteType(0)
        );
        this.volumeSegmentationHelper = new VolumeSegmentationHelper(
                alignmentSpace,
                getFirstFoundVariant(
                        Arrays.asList(
                                queryVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                                queryVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
                        )
                ),
                executorService
        );
    }

    @Override
    public boolean isAvailable() {
        return volumeSegmentationHelper.isAvailable();
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.SkeletonSWC, ComputeFileType.Vol3DSegmentation);
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> sourceTargetImage,
                                                  Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> targetVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        if (!volumeSegmentationHelper.isAvailable()) {
            LOG.info("Bidirectional score cannot be computed - query 3D volume is not available");
            return new ShapeMatchScore(-1);
        }
        ComputeVariantImageSupplier<? extends IntegerType<?>> targetImageSupplier = getFirstFoundVariant(
                Arrays.asList(
                        targetVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                        targetVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
                )
        );
        if (targetImageSupplier == null) {
            LOG.info("No target 3D-volume provided");
            return new ShapeMatchScore(-1);
        }
        RandomAccessibleInterval<? extends IntegerType<?>> target3DImage = targetImageSupplier.getImage();
        if (target3DImage == null) {
            LOG.info("No target 3D-volume could be loaded for {}", targetImageSupplier.getName());
            return new ShapeMatchScore(-1);
        }
        RandomAccessibleInterval<? extends RGBPixelType<?>> targetSegmentedCDM = volumeSegmentationHelper.generateSegmentedCDM(
            target3DImage
        );
        if (targetSegmentedCDM == null) {
            // the 3D images are not available or there's no overlap in 3-D
            return new ShapeMatchScore(-1);
        }
        RandomAccessibleInterval<UnsignedByteType> targetSignal = ImageTransforms.rgbToSignalTransformation(
                targetSegmentedCDM, 1
        );
        long targetSignalArea = ImageAccessUtils.fold(targetSignal,
                0L, (a, p) -> a + p.getInteger(), Long::sum
        );
        LOG.debug("Target signal area {}", targetSignalArea);
        RandomAccessibleInterval<UnsignedShortType> queryToTargetGapsImage = ImageTransforms.createBinaryPixelOperation(
                targetSignal,
                queryGradientImg,
                (p1, p2, r) -> {
                    int p1Val = p1.get();
                    int p2Val = p2.get();
                    int gapVal = p1Val * p2Val;
                    r.set(gapVal > GAP_THRESHOLD ? gapVal : 0);
                },
                new UnsignedShortType()
        );
        long queryToTargetGradientAreaGap = ImageAccessUtils.fold(queryToTargetGapsImage,
                0L, (a, p) -> a + p.getInteger(), Long::sum
        );
        LOG.debug("Query to target gradient area gap between {} and {} -> {}",
                volumeSegmentationHelper.getQuery3DVolumeName(),
                targetImageSupplier.getName(),
                queryToTargetGradientAreaGap);

        Img<? extends RGBPixelType<?>> dilatedTargetSegmentedCDM = MaxFilterAlgorithm.rgbMaxFilterInXandY(
                targetSegmentedCDM,
                10,
                queryThreshold
        );
        Img<UnsignedShortType> targetGradientImg = DistanceTransformAlgorithm.generateDistanceTransformWithoutDilation(dilatedTargetSegmentedCDM);
        RandomAccessibleInterval<UnsignedShortType> targetToQueryGapsImage = ImageTransforms.createBinaryPixelOperation(
                querySignal,
                targetGradientImg,
                (p1, p2, r) -> {
                    int p1Val = p1.get();
                    int p2Val = p2.get();
                    int gapVal = p1Val * p2Val;
                    r.set(gapVal > GAP_THRESHOLD ? gapVal : 0);
                },
                new UnsignedShortType()
        );
        long targetToQueryGradientAreaGap = ImageAccessUtils.fold(targetToQueryGapsImage,
                0L, (a, p) -> a + p.getInteger(), Long::sum
        );
        LOG.debug("Target to query gradient area gap between {} and {} -> {}",
                volumeSegmentationHelper.getQuery3DVolumeName(),
                targetImageSupplier.getName(),
                targetToQueryGradientAreaGap);

        long score = (queryToTargetGradientAreaGap + targetToQueryGradientAreaGap) / 2;
        long endTime = System.currentTimeMillis();
        LOG.debug("Final negative score between {} and {} is {} - computed in {} secs",
                volumeSegmentationHelper.getQuery3DVolumeName(),
                targetImageSupplier.getName(),
                score, (endTime - startTime)/1000.);

        return new ShapeMatchScore(score);
    }

}
