package org.janelia.colormipsearch.cds;

import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.RectIntervalHelper;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore, P extends RGBPixelType<P>, G extends IntegerType<G>> implements ColorDepthSearchAlgorithm<S, P, G> {

    static class QueryAccess<P extends Type<P>> implements RandomAccessibleInterval<P> {

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

    QueryAccess<P> queryImage;
    final int targetThreshold;
    final double zTolerance;

    @SuppressWarnings("unchecked")
    protected AbstractColorDepthSearchAlgorithm(@Nonnull RandomAccessibleInterval<P> queryImage,
                                                int queryThreshold,
                                                int targetThreshold,
                                                double zTolerance) {
        this.targetThreshold = targetThreshold;
        this.zTolerance = zTolerance;
        this.queryImage = getMaskPosArray(queryImage, queryThreshold);
    }

    @Override
    public RandomAccessibleInterval<P> getQueryImage() {
        return queryImage;
    }

    public long getQuerySize() {
        return ImageAccessUtils.getMaxSize(queryImage.dimensionsAsLongArray());
    }

    private QueryAccess<P> getMaskPosArray(RandomAccessibleInterval<P> msk, int thresm) {
        BiPredicate<long[], P> isRGBBelowThreshold = (long[] pos, P pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= thresm && g <= thresm && b <= thresm;
        };
        RandomAccessibleInterval<P> thresholdMaskedAccess = ImageTransforms.maskPixelsMatchingCond(
                msk, isRGBBelowThreshold, null
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

}
