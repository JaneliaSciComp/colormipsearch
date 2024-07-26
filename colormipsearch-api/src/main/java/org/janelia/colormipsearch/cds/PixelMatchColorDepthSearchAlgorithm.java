package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.GeomTransform;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.MirrorTransform;
import org.janelia.colormipsearch.image.RectIntervalHelper;
import org.janelia.colormipsearch.image.ShiftTransform;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * PixelMatchColorDepthQuerySearchAlgorithm - implements the color depth mip comparison
 * using internal arrays containg the positions from the mask that are above the mask threshold
 * and the positions after applying the specified x-y shift and mirroring transformations.
 * The mask pixels are compared against the target pixels tht
 */
public class PixelMatchColorDepthSearchAlgorithm extends AbstractColorDepthSearchAlgorithm<PixelMatchScore> {

    static class QueryAccess<P extends RGBPixelType<P>>  implements RandomAccessibleInterval<P> {

        private final RandomAccessibleInterval<P> source;
        private final RectIntervalHelper rectIntervalHelper;

        private final int[] selectedPixelPositions;
        private final int[][] allShiftedSelectedPixelPositions;
        private final int[][] allShiftedMirroredSelectedPixelPositions;


        QueryAccess(RandomAccessibleInterval<P> source,
                    int[] selectedPixelPositions,
                    int[][] allShiftedSelectedPixelPositions,
                    int[][] allShiftedMirroredSelectedPixelPositions) {
            this.source = source;
            this.selectedPixelPositions = selectedPixelPositions;
            this.allShiftedSelectedPixelPositions = allShiftedSelectedPixelPositions;
            this.allShiftedMirroredSelectedPixelPositions = allShiftedMirroredSelectedPixelPositions;
            this.rectIntervalHelper = new RectIntervalHelper(source);
        }

        @Override
        public long min(int d) {
            return source.min(d);
        }

        @Override
        public long max(int d) {
            return source.max(d);
        }

        @Override
        public RandomAccess<P> randomAccess() {
            return source.randomAccess();
        }

        @Override
        public RandomAccess<P> randomAccess(Interval interval) {
            return source.randomAccess(interval);
        }

        @Override
        public int numDimensions() {
            return source.numDimensions();
        }

        int[] getSelectedPixelPositions() {
            return selectedPixelPositions;
        }

        int[][] getAllShiftedSelectedPixelPositions() {
            return allShiftedSelectedPixelPositions;
        }

        boolean hasMirroredPositions() {
            return allShiftedMirroredSelectedPixelPositions != null;
        }

        int[][] getAllShiftedMirroredSelectedPixelPositions() {
            return allShiftedMirroredSelectedPixelPositions;
        }

        void localize(int index, long[] location) {
            rectIntervalHelper.unsafeLinearIndexToRectCoords(index, location);
        }
    }

    private QueryAccess<? extends RGBPixelType<?>> queryImage;
    private final double zTolerance;

    public PixelMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                               @Nullable BiPredicate<long[], IntegerType<?>> excludedRegionCondition,
                                               int queryThreshold,
                                               int targetThreshold,
                                               boolean withMirrorFlag,
                                               double zTolerance, int shiftValue) {
        super(queryThreshold, targetThreshold, withMirrorFlag);
        this.zTolerance = zTolerance;
        this.queryImage = getMaskPosArray(queryImage, excludedRegionCondition, queryThreshold, shiftValue);
    }

    private <P extends RGBPixelType<P>> QueryAccess<P> getMaskPosArray(RandomAccessibleInterval<? extends RGBPixelType<?>> query,
                                                                       @Nullable BiPredicate<long[], IntegerType<?>> excludedRegionCondition,
                                                                       int thresm,
                                                                       int shiftValue) {
        BiPredicate<long[], P> isRGBBelowThreshold = (long[] pos, P pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= thresm && g <= thresm && b <= thresm;
        };
        BiPredicate<long[], P> pixelCond = excludedRegionCondition != null
                ? isRGBBelowThreshold.or(excludedRegionCondition)
                : isRGBBelowThreshold;

        RandomAccessibleInterval<P> queryAccessibleImage = applyMaskCond(query, pixelCond);
        RectIntervalHelper rectIntervalHelper = new RectIntervalHelper(queryAccessibleImage);
        long[] tmpPos = new long[rectIntervalHelper.numDimensions()];
        int[] pixelPositions = ImageAccessUtils.stream(Views.flatIterable(queryAccessibleImage).cursor(), false)
                .filter(pos -> !pos.get().isZero())
                .mapToInt(pos -> {
                    pos.localize(tmpPos);
                    return rectIntervalHelper.rectCoordsToIntLinearIndex(tmpPos);
                })
                .toArray();
        GeomTransform[] shiftTransforms = generateShiftTransforms(rectIntervalHelper.numDimensions(), shiftValue);
        GeomTransform mirrorTransform = withMirrorFlag
                ? new MirrorTransform(queryAccessibleImage.minAsLongArray(), queryAccessibleImage.maxAsLongArray(), 0)
                : null;

        int[][] targetShiftedPixelPositions = new int[shiftTransforms.length][];
        int[][] targetShiftedMirroredPositions = withMirrorFlag ? new int[shiftTransforms.length][] : null;
        for (int ti = 0; ti < shiftTransforms.length; ti++) {
            GeomTransform currentTransform = shiftTransforms[ti];
            targetShiftedPixelPositions[ti] = applyTransformToPixelPos(currentTransform, rectIntervalHelper, pixelPositions);
            if (withMirrorFlag) {
                targetShiftedMirroredPositions[ti] = applyTransformToPixelPos(currentTransform.andThen(mirrorTransform), rectIntervalHelper, pixelPositions);
            }
        }

        return new QueryAccess<>(
                ImageAccessUtils.materializeAsNativeImg(queryAccessibleImage, null, null),
                pixelPositions,
                targetShiftedPixelPositions,
                targetShiftedMirroredPositions
        );
    }

    private GeomTransform[] generateShiftTransforms(int ndims, int shiftValue) {
        // the image is shifted in increments of 2 pixels, i.e. 2, 4, 6, ...
        // so the shiftValue must be an even number
        if ((shiftValue & 0x1) == 0x1) {
            throw new IllegalArgumentException("Invalid shift value: " + shiftValue +
                    " - the targets should be shifted in increments of 2 pixels because 1 pixel shift is too small");
        }
        return ImageAccessUtils.streamNeighborsWithinDist(ndims, shiftValue/2, true)
                .map(c -> CoordUtils.mulCoords(c, 2))
                .map(ShiftTransform::new)
                .toArray(GeomTransform[]::new);
    }

    private int[] applyTransformToPixelPos(GeomTransform transform, RectIntervalHelper rectIntervalHelper, int[] pixelPositions) {
        long[] currentPos = new long[rectIntervalHelper.numDimensions()];
        long[] transformedPos = new long[rectIntervalHelper.numDimensions()];
        return Arrays.stream(pixelPositions)
                .mapToObj(pi -> {
                    rectIntervalHelper.unsafeLinearIndexToRectCoords(pi, currentPos);
                    transform.apply(currentPos, transformedPos);
                    return transformedPos;
                })
                .mapToInt(pos -> {
                    // there has to be a 1:1 correspondence with the query pixel pos
                    // so where the transformed pos falls out of the interval we return -1
                    if (rectIntervalHelper.contains(pos)) {
                        return rectIntervalHelper.rectCoordsToIntLinearIndex(pos);
                    } else {
                        return -1;
                    }
                })
                .toArray();
    }

    @Override
    public RandomAccessibleInterval<? extends RGBPixelType<?>> getQueryImage() {
        return queryImage;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return Collections.emptySet();
    }

    @Override
    public PixelMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> sourceTargetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        long querySize = ImageAccessUtils.getMaxSize(queryImage.dimensionsAsLongArray());;
        if (querySize == 0) {
            return new PixelMatchScore(0, 0, false);
        } else if (ImageAccessUtils.differentShape(getQueryImage(), sourceTargetImage)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid image size - target's image shape %s must match query's image: %s",
                    Arrays.toString(sourceTargetImage.dimensionsAsLongArray()), Arrays.toString(getQueryImage().dimensionsAsLongArray())));
        }
        // apply the threshold to the target
        RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage = applyRGBThreshold(sourceTargetImage, targetThreshold);

        boolean bestScoreMirrored = false;
        int matchingPixelsScore = Arrays.stream(queryImage.getAllShiftedSelectedPixelPositions())
                .mapToInt(targetPixels -> countColorDepthMatches(queryImage, targetImage, targetPixels))
                .max()
                .orElse(0);
        if (withMirrorFlag) {
            int mirroredMatchingScore = Arrays.stream(queryImage.getAllShiftedMirroredSelectedPixelPositions())
                    .mapToInt(targetPixels -> countColorDepthMatches(queryImage, targetImage, targetPixels))
                    .max()
                    .orElse(0);
            if (mirroredMatchingScore > matchingPixelsScore) {
                matchingPixelsScore = mirroredMatchingScore;
                bestScoreMirrored = true;
            }
        }
        double matchingPixelsRatio = (double)matchingPixelsScore / (double)querySize;
        return new PixelMatchScore(matchingPixelsScore, matchingPixelsRatio, bestScoreMirrored);
    }

    private int countColorDepthMatches(QueryAccess<? extends RGBPixelType<?>> queryImage,
                                       RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage,
                                       int[] targetPositions) {
        CDPixelMatchOp pixelMatchOp = new CDPixelMatchOp();
        long[] queryPos = new long[queryImage.numDimensions()];
        long[] targetPos = new long[queryImage.numDimensions()];
        RandomAccess<? extends RGBPixelType<?>> queryAccess = queryImage.randomAccess();
        RandomAccess<? extends RGBPixelType<?>> targetAccess = targetImage.randomAccess();
        int[] queryPositions = queryImage.getSelectedPixelPositions();
        assert queryPositions.length == targetPositions.length;
        int score = 0;
        for (int pi = 0; pi < queryPositions.length; pi++) {
            if (targetPositions[pi] < 0) {
                continue;
            }
            queryImage.localize(queryPositions[pi], queryPos);
            queryImage.localize(targetPositions[pi], targetPos);
            RGBPixelType<?> qp = queryAccess.setPositionAndGet(queryPos);
            RGBPixelType<?> tp = targetAccess.setPositionAndGet(targetPos);
            if (tp.isNotZero()) {
                // we only need to check the target because the query pixels were selected using that criteria
                int qred = qp.getRed();
                int qgreen = qp.getGreen();
                int qblue = qp.getBlue();
                int tred = tp.getRed();
                int tgreen = tp.getGreen();
                int tblue = tp.getBlue();
                double pxGap = pixelMatchOp.calculatePixelGapFromRGBValues(qred, qgreen, qblue, tred, tgreen, tblue);
                if (pxGap <= zTolerance) score++;
            }
        }
        return score;
    }

}
