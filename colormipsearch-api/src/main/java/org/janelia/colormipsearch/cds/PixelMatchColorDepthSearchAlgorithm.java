package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.GeomTransform;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
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

        QueryAccess(RandomAccessibleInterval<P> source,
                    int[] selectedPixelPositions) {
            this.source = source;
            this.selectedPixelPositions = selectedPixelPositions;
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

        void localize(int index, long[] location) {
            rectIntervalHelper.unsafeLinearIndexToRectCoords(index, location);
        }
    }

    private QueryAccess<? extends RGBPixelType<?>> queryImage;
    private final double zTolerance;
    private final GeomTransform[] shiftTransforms;

    public PixelMatchColorDepthSearchAlgorithm(RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
                                               int queryThreshold,
                                               int targetThreshold,
                                               boolean withMirrorFlag,
                                               double zTolerance, int shiftValue) {
        super(queryThreshold, targetThreshold, withMirrorFlag);

        this.zTolerance = zTolerance;
        this.queryImage = getMaskPosArray(queryImage, queryThreshold);
        // shifting
        shiftTransforms = generateShiftTransforms(shiftValue);
    }

    private <P extends RGBPixelType<P>> QueryAccess<P> getMaskPosArray(RandomAccessibleInterval<? extends RGBPixelType<?>> msk, int thresm) {
        BiPredicate<long[], P> isRGBBelowThreshold = (long[] pos, P pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= thresm && g <= thresm && b <= thresm;
        };
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<P> thresholdMaskedAccess = ImageTransforms.maskPixelsMatchingCond(
                (RandomAccessibleInterval<P>) msk,
                isRGBBelowThreshold, null
        );
        RectIntervalHelper rectIntervalHelper = new RectIntervalHelper(thresholdMaskedAccess);
        long[] tmpPos = new long[rectIntervalHelper.numDimensions()];
        int[] pixelPositions = ImageAccessUtils.stream(Views.flatIterable(thresholdMaskedAccess).cursor(), false)
                .filter(pos -> !pos.get().isZero())
                .mapToInt(pos -> {
                    pos.localize(tmpPos);
                    return rectIntervalHelper.rectCoordsToIntLinearIndex(tmpPos);
                })
                .toArray();
        return new QueryAccess<>(
                thresholdMaskedAccess,
                pixelPositions
        );
    }

    private GeomTransform[] generateShiftTransforms(int shiftValue) {
        // the image is shifted in increments of 2 pixels, i.e. 2, 4, 6, ...
        // so the shiftValue must be an even number
        if ((shiftValue & 0x1) == 0x1) {
            throw new IllegalArgumentException("Invalid shift value: " + shiftValue +
                    " - the targets is shifted in increments of 2 pixels because 1 pixel shift is too small");
        }
        int ndims = getQueryImage().numDimensions();
        return ImageAccessUtils.streamNeighborsWithinDist(ndims, shiftValue/2, true)
                .map(c -> CoordUtils.mulCoords(c, 2))
                .map(ShiftTransform::new)
                .toArray(GeomTransform[]::new);
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
    public PixelMatchScore calculateMatchingScore(@Nonnull RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage,
                                                  Map<ComputeFileType, Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> targetVariantsSuppliers) {
        long querySize = ImageAccessUtils.getMaxSize(queryImage.dimensionsAsLongArray());;
        if (querySize == 0) {
            return new PixelMatchScore(0, 0, false);
        } else if (ImageAccessUtils.differentShape(getQueryImage(), targetImage)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid image size - target's image shape %s must match query's image: %s",
                    Arrays.toString(targetImage.dimensionsAsLongArray()), Arrays.toString(getQueryImage().dimensionsAsLongArray())));
        }
        boolean bestScoreMirrored = false;
        @SuppressWarnings("unchecked")
        int matchingPixelsScore = Arrays.stream(shiftTransforms)
                .map(transform -> applyTransformToImage(targetImage, transform))
                .mapToInt(targetPixelAccess -> countColorDepthMatches(queryImage, (RandomAccessibleInterval<? extends RGBPixelType<?>>) targetPixelAccess))
                .max()
                .orElse(0);
        if (withMirrorFlag) {
            GeomTransform mirrorTransform = new MirrorTransform(
                    getQueryImage().minAsLongArray(),
                    getQueryImage().maxAsLongArray(),
                    0);
            @SuppressWarnings("unchecked")
            int mirroredMatchingScore = Arrays.stream(shiftTransforms)
                    .map(geomTransform -> geomTransform.compose(mirrorTransform))
                    .map(transform -> applyTransformToImage(targetImage, transform))
                    .mapToInt(targetPixelAccess -> countColorDepthMatches(queryImage, (RandomAccessibleInterval<? extends RGBPixelType<?>>) targetPixelAccess))
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
                                       RandomAccessibleInterval<? extends RGBPixelType<?>> targetImage) {
        CDPixelMatchOp pixelMatchOp = new CDPixelMatchOp();
        long[] pos = new long[queryImage.numDimensions()];
        RandomAccess<? extends RGBPixelType<?>> queryAccess = queryImage.randomAccess();
        RandomAccess<? extends RGBPixelType<?>> targetAccess = targetImage.randomAccess();
        return Arrays.stream(queryImage.getSelectedPixelPositions())
                .map(pixelIndex -> {
                    queryImage.localize(pixelIndex, pos);
                    RGBPixelType<?> qp = queryAccess.setPositionAndGet(pos);
                    RGBPixelType<?> tp = targetAccess.setPositionAndGet(pos);
                    int tred = tp.getRed();
                    int tgreen = tp.getGreen();
                    int tblue = tp.getBlue();

                    if (tred > targetThreshold || tgreen > targetThreshold || tblue > targetThreshold) {
                        int qred = qp.getRed();
                        int qgreen = qp.getGreen();
                        int qblue = qp.getBlue();
                        double pxGap = pixelMatchOp.calculatePixelGapFromRGBValues(qred, qgreen, qblue, tred, tgreen, tblue);
                        return pxGap <= zTolerance ? 1 : 0;
                    } else {
                        return 0;
                    }
                })
                .reduce(0, Integer::sum);
    }

}
