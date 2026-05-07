package org.janelia.colormipsearch.cds;

import java.awt.Image;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.ColorOperations;
import org.janelia.colormipsearch.image.Gray8ByteImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.imageprocessing.IntQuadOperator;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates the gradient area gap between an encapsulated EM mask and an LM (segmented) image.
 */
public class Shape2DMatchColorDepthSearchAlgorithm implements ColorDepthSearchAlgorithm<ShapeMatchScore> {

    private static final Logger LOG = LoggerFactory.getLogger(Shape2DMatchColorDepthSearchAlgorithm.class);
    private static final int GAP_THRESHOLD = 3;

    private static final IntQuadOperator PIXEL_GAP_OP = (queryPix, queryMask, targetGradPix, targetDilatedPix) -> {
        int gap;
        if ((queryPix & 0xFFFFFF) != 0 && (targetDilatedPix & 0xFFFFFF) != 0) {
            int pxGapSlice = GradientAreaGapUtils.calculateSliceGap(queryPix, targetDilatedPix);
            if (40 <= pxGapSlice - 40) {
                // negative score value
                gap = pxGapSlice - 40;
            } else {
                gap = queryMask * targetGradPix;
            }
        } else {
            gap = queryMask * targetGradPix;
        }
        return gap > GAP_THRESHOLD ? gap : 0;
    };

    static ImageArray computeHighExpressionBinaryMask(ImageArray imageArray, int r1, int r2) {
        ImageArray r1Dilation = ImageOperations.rgbMaxFilter2D(imageArray, r1, r1);
        ImageArray r2Dilation = ImageOperations.rgbMaxFilter2D(imageArray, r2, r2);
        return ImageOperations.duplicateImage(
                ImageOperations.binaryMask(
                        ImageOperations.combine2(
                                r1Dilation, r2Dilation,
                                (p1, p2) -> (p2 & 0xFFFFFF) != 0 ? 0xFF000000 : p1
                        ),
                        0,
                        1
                )
                ,
                Gray8ByteImageArray::new
        );
    }


    private final ImageArray queryImage;
    private final ImageArray binaryQueryMask;
    private final ImageArray binaryHighExpressionQueryMask; // pix(x,y) = 1 if there's too much expression surrounding x,y
    private final ImageArray queryROIMaskImage;
    private final int queryThreshold;
    private final boolean mirrorQuery;
    private final ImageMaskPredicate labelsMaskPredicate;

    Shape2DMatchColorDepthSearchAlgorithm(ImageArray queryImage,
                                          ImageArray queryROIMaskImage,
                                          int queryThreshold,
                                          boolean mirrorQuery,
                                          ImageMaskPredicate labelsMaskPredicate) {
        this.queryImage = ImageOperations.duplicateImage(
                ImageOperations.maskRGB(
                        ImageOperations.maskRegion(queryImage, labelsMaskPredicate),
                        queryThreshold
                ),
                RGBByteImageArray::new
        );
        this.binaryQueryMask = ImageOperations.duplicateImage(
                ImageOperations.binaryMask(
                        ImageOperations.rgbToGray8(this.queryImage),
                        2,
                        1
                ),
                Gray8ByteImageArray::new
        );
        this.binaryHighExpressionQueryMask = Shape2DMatchColorDepthSearchAlgorithm.computeHighExpressionBinaryMask(
                this.queryImage, 60, 20
        );
        this.queryROIMaskImage = queryROIMaskImage;
        this.queryThreshold = queryThreshold;
        this.mirrorQuery = mirrorQuery;
        this.labelsMaskPredicate = labelsMaskPredicate;
    }

    @Override
    public ImageArray getQueryImage() {
        return queryImage;
    }

    @Override
    public int getQuerySize() {
        int s = 0;
        for (int pi = 0; pi < queryImage.getSpatialSize(); pi++) {
            if (queryImage.getPackedIntValAtIndex(pi) != 0)
                s++;
        }
        return s;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return EnumSet.of(ComputeFileType.GradientImage, ComputeFileType.ZGapImage);
    }

    /**
     * Calculate area gap between the encapsulated mask and the given image with the corresponding image gradients and zgaps.
     * The gradient image must be non-null but the z-gap image can be null in which case it is calculated using
     * a dilation transformation.
     *
     * @param targetImageArray
     * @param variantImageSuppliers
     * @return shape score between the current query and the specified target
     */
    @Override
    public ShapeMatchScore calculateMatchingScore(@Nonnull ImageArray targetImageArray,
                                                  Map<ComputeFileType, ComputeVariantImageSupplier> variantImageSuppliers) {
        long startTime = System.currentTimeMillis();
        ImageArray targetGradientImageArray = getVariantImageArray(variantImageSuppliers.get(ComputeFileType.GradientImage));
        ImageArray targetZGapMaskImageArray = getVariantImageArray(variantImageSuppliers.get(ComputeFileType.ZGapImage));
        if (targetGradientImageArray == null || targetZGapMaskImageArray == null) {
            LOG.info("Skip negative score because no gradient or zgap images were provided");
            return new ShapeMatchScore(-1, -1, -1, false);
        }
        ImageArray targetImage = ImageOperations.duplicateImage(
                ImageOperations.maskRegion(targetImageArray, labelsMaskPredicate),
                RGBByteImageArray::new
        );
        // Materialize the target and zgap mask so it's not recomputed on each traversal (forward + mirrored)
        ImageArray targetZGapMaskImage = ImageOperations.duplicateImage(
                ImageOperations.maskRGB(targetZGapMaskImageArray, queryThreshold),
                RGBByteImageArray::new
        );

        ShapeMatchScore negativeScores = calculateNegativeScores(
                targetImage,
                targetGradientImageArray,
                targetZGapMaskImage,
                0,
                false
        );

        if (mirrorQuery) {
            LOG.trace("Start calculating area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            ShapeMatchScore mirrorNegativeScores = calculateNegativeScores(
                    targetImage,
                    targetGradientImageArray,
                    targetZGapMaskImage,
                    Dimensions.X_AXIS,
                    true
            );
            LOG.trace("Completed area gap score for mirrored mask {}ms", System.currentTimeMillis() - startTime);
            if (mirrorNegativeScores.getScore() < negativeScores.getScore()) {
                return mirrorNegativeScores;
            }
        }
        return negativeScores;
    }

    private ImageArray getVariantImageArray(ComputeVariantImageSupplier variantImageSupplier) {
        if (variantImageSupplier != null) {
            return variantImageSupplier.getImage();
        } else {
            return null;
        }
    }

    private ShapeMatchScore calculateNegativeScores(ImageArray targetImage,
                                                    ImageArray targetGradientImage,
                                                    ImageArray targetZGapMaskImage,
                                                    int flippedAxes,
                                                    boolean useMirroredMask) {
        long startTime = System.currentTimeMillis();
        ImageArray roiQueryImage;
        ImageArray roiBinaryQueryMask;
        ImageArray roiQueryHighExpressionMask;
        if (queryROIMaskImage == null) {
            roiQueryImage = ImageOperations.flipImage(queryImage, flippedAxes);
            roiBinaryQueryMask = ImageOperations.flipImage(binaryQueryMask, flippedAxes);
            roiQueryHighExpressionMask = ImageOperations.flipImage(binaryHighExpressionQueryMask, flippedAxes);
        } else {
            IntBinaryOperator maskOperator = (p1, m) -> (m & 0xFFFFFF) == 0 ? 0 : p1;
            roiQueryImage = ImageOperations.combine2(
                    ImageOperations.flipImage(queryImage, flippedAxes),
                    queryROIMaskImage,
                    maskOperator
            );
            roiBinaryQueryMask = ImageOperations.combine2(
                    ImageOperations.flipImage(binaryQueryMask, flippedAxes),
                    queryROIMaskImage,
                    maskOperator
            );
            roiQueryHighExpressionMask = ImageOperations.combine2(
                    ImageOperations.flipImage(binaryHighExpressionQueryMask, flippedAxes),
                    queryROIMaskImage,
                    maskOperator
            );
        }
        ImageArray gaps = ImageOperations.combine4(
                roiQueryImage,
                roiBinaryQueryMask,
                targetGradientImage,
                ImageOperations.flipImage(targetZGapMaskImage, flippedAxes),
                PIXEL_GAP_OP
        );
        ImageArray highExpressionRegions = ImageOperations.combine2(
                targetImage,
                roiQueryHighExpressionMask,
                (p1, p2) -> {
                    if (p2 == 1) {
                        if (ColorOperations.red(p1) > queryThreshold || ColorOperations.green(p1) > queryThreshold || ColorOperations.blue(p1) > queryThreshold) {
                            return 1;
                        }
                    }
                    return 0;
                });
        long gradientAreaGap = ImageOperations.sum(gaps);
        LOG.trace("Gradient area gap: {} (calculated in {}ms)", gradientAreaGap, System.currentTimeMillis() - startTime);
        long highExpressionArea = ImageOperations.sum(highExpressionRegions);
        LOG.trace("High expression area: {} (calculated in {}ms)", highExpressionArea, System.currentTimeMillis() - startTime);
        return new ShapeMatchScore(gradientAreaGap, highExpressionArea, -1, useMirroredMask);
    }

}
