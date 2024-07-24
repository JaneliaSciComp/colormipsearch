package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

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
    private final RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask;
    private final BiPredicate<long[], ? extends IntegerType<?>> excludedRegionCondition;
    private final VolumeSegmentationHelper volumeSegmentationHelper;

    @SuppressWarnings("unchecked")
    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> sourceQueryImage,
                                                       Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantsSuppliers,
                                                       BiPredicate<long[], ? extends IntegerType<?>> excludedRegionCondition,
                                                       RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
                                                       String alignmentSpace,
                                                       int queryThreshold,
                                                       int targetThreshold,
                                                       boolean withMirrorFlag) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        this.queryImageAccess = applyRGBThreshold(
                (RandomAccessibleInterval<? extends RGBPixelType<?>>) applyMaskCond(sourceQueryImage, (BiPredicate<long[], RGBPixelType<?>>)excludedRegionCondition),
                queryThreshold
        );
        this.excludedRegionCondition = excludedRegionCondition;
        this.queryGradientImg = DistanceTransformAlgorithm.generateDistanceTransform(
                queryImageAccess,
                5);

        this.querySignal = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.rgbToSignalTransformation(queryImageAccess, queryThreshold),
                null,
                new UnsignedByteType(0)
        );
        this.queryROIMask = queryROIMask;
        this.volumeSegmentationHelper = new VolumeSegmentationHelper(
                alignmentSpace,
                getFirstReifiableVariant(
                        Arrays.asList(
                                queryVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                                queryVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
                        )
                )
        );
    }

    @Override
    public RandomAccessibleInterval<? extends RGBPixelType<?>> getQueryImage() {
        return queryImageAccess;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return Collections.emptySet();
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> sourceTargetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        RandomAccessibleInterval<? extends IntegerType<?>> target3DImage = getFirstReifiableVariant(
                Arrays.asList(
                        targetVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                        targetVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
                )
        );
        RandomAccessibleInterval<? extends RGBPixelType<?>> targetSegmentedCDM = volumeSegmentationHelper.generateSegmentedCDM(
            target3DImage
        );
        if (targetSegmentedCDM == null) {
            // the 3D images are not available
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
        System.out.printf("Query to target gradient area gap: %d\n", queryToTargetGradientAreaGap);

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
        System.out.printf("Target to query gradient area gap: %d\n", targetToQueryGradientAreaGap);

        long score = (queryToTargetGradientAreaGap + targetToQueryGradientAreaGap) / 2;
        long endTime = System.currentTimeMillis();
        System.out.printf("Final negative score: %d - computed in %f secs\n", score, (endTime - startTime)/1000.);

        return new ShapeMatchScore(score);
    }

}
