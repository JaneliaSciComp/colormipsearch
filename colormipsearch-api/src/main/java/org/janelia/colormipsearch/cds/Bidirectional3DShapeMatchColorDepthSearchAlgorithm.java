package org.janelia.colormipsearch.cds;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.checkerframework.checker.units.qual.A;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.algorithms.DistanceTransformAlgorithm;
import org.janelia.colormipsearch.image.algorithms.MaxFilterAlgorithm;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.scijava.test.TestUtils;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Bidirectional3DShapeMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final int GAP_THRESHOLD = 3;

    private final RandomAccessibleInterval<? extends RGBPixelType<?>> queryImageAccess;
    private final RandomAccessibleInterval<UnsignedShortType> queryGradientImg;
    private final RandomAccessibleInterval<UnsignedByteType> querySignal;
    private final RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask;
    private final VolumeSegmentationHelper volumeSegmentationHelper;

    Bidirectional3DShapeMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                                       Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> queryVariantsSuppliers,
                                                       RandomAccessibleInterval<? extends IntegerType<?>> queryROIMask,
                                                       String alignmentSpace,
                                                       int queryThreshold,
                                                       int targetThreshold,
                                                       boolean withMirrorFlag) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        this.queryImageAccess = applyThreshold(queryImage, queryThreshold);
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
                        queryVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                        queryVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
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
    public ShapeMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        long startTime = System.currentTimeMillis();
        RandomAccessibleInterval<? extends IntegerType<?>> target3DImage = getFirstReifiableVariant(
                targetVariantsSuppliers.get(ComputeFileType.Vol3DSegmentation),
                targetVariantsSuppliers.get(ComputeFileType.SkeletonSWC)
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
        System.out.printf("Target signal area %d\n", targetSignalArea);
        ImageJFunctions.show(queryGradientImg, "Query gradient");
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

        Img<? extends RGBPixelType<?>> dilatedTargetSegmentedCDM = MaxFilterAlgorithm.rgbMaxFilterIn2D(
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

    @SuppressWarnings("unchecked")
    private <T extends RGBPixelType<T>> void displayRGBImage(RandomAccessibleInterval<? extends RGBPixelType<?>> rgbImage,
                                                             String title) {
        displayImage(
                (RandomAccessibleInterval<T>) rgbImage,
                (T rgb, ARGBType p) -> p.set(rgb.getInteger()),
                new ARGBType(0),
                title
        );
    }

    private <S extends IntegerType<S>, T extends NumericType<T>> void displayImage(
            RandomAccessibleInterval<S> image,
            Converter<S, T> displayConverter,
            T background,
            String title) {
        RandomAccessibleInterval<T> displayableImage = ImageTransforms.createPixelTransformation(
                image,
                displayConverter,
                () -> background
        );
        ImageJFunctions.show(displayableImage, title);
    }

}
