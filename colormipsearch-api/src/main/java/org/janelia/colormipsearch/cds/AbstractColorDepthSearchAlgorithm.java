package org.janelia.colormipsearch.cds;

import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.IterableRandomAccess;
import org.janelia.colormipsearch.image.MaskedPixelAccess;
import org.janelia.colormipsearch.image.PixelPositionsListCursor;
import org.janelia.colormipsearch.image.RectCoordsHelper;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> implements ColorDepthSearchAlgorithm<S> {

    private static class QueryAccess<T> implements ImageAccess<T> {
        private final RandomAccess<T> randomAccess;
        private final Interval interval;
        private final Cursor<T> cursor;
        private final T backgroundValue;
        private final long size;

        QueryAccess(RandomAccess<T> randomAccess,
                    Interval interval,
                    Cursor<T> cursor,
                    T backgroundValue,
                    long size) {
            this.randomAccess = randomAccess;
            this.interval = interval;
            this.cursor = cursor;
            this.backgroundValue = backgroundValue;
            this.size = size;
        }

        @Override
        public Interval getInterval() {
            return interval;
        }

        @Override
        public RandomAccess<T> getRandomAccess() {
            return randomAccess;
        }

        @Override
        public Cursor<T> getCursor() {
            cursor.reset();
            return cursor;
        }

        @Override
        public T getBackgroundValue() {
            return backgroundValue;
        }

        @Override
        public long getSize() {
            return size;
        }
    }

    private final QueryAccess<? extends RGBPixelType<?>> queryImage;
    final int targetThreshold;
    final double zTolerance;

    @SuppressWarnings("unchecked")
    protected AbstractColorDepthSearchAlgorithm(@Nonnull ImageAccess<? extends RGBPixelType<?>> queryImage,
                                                int queryThreshold,
                                                int targetThreshold,
                                                double zTolerance) {
        this.targetThreshold = targetThreshold;
        this.zTolerance = zTolerance;
        this.queryImage = getMaskPosArray((ImageAccess<RGBPixelType<?>>)queryImage, queryThreshold);
    }

    @Override
    public ImageAccess<? extends RGBPixelType<?>> getQueryImage() {
        return queryImage;
    }

    private QueryAccess<? extends RGBPixelType<?>> getMaskPosArray(ImageAccess<RGBPixelType<?>> msk,
                                                                   int thresm) {
        RectCoordsHelper coordHelper = new RectCoordsHelper(msk.getInterval());

        IterableRandomAccess<RGBPixelType<?>> mskAccess = new MaskedPixelAccess<>(
                msk.getRandomAccess(),
                msk.getInterval(),
                (long[] pos, RGBPixelType<?> pixel) -> {
                    int r = pixel.getRed();
                    int g = pixel.getGreen();
                    int b = pixel.getBlue();
                    // mask the pixel if all channels are under the threshold
                    return r <= thresm && g <= thresm && b <= thresm;
                },
                msk.getBackgroundValue()
        );
        int[] pixelPositions = ImageAccessUtils.stream(mskAccess)
                .mapToInt(pos -> coordHelper.rectCoordsToIntLinearIndex(pos.positionAsLongArray()))
                .toArray();

        PixelPositionsListCursor<RGBPixelType<?>> mskCursor = new PixelPositionsListCursor<>(
                mskAccess, msk.getInterval().dimensionsAsLongArray(), pixelPositions
        );
        return new QueryAccess<>(
                mskAccess,
                msk.getInterval(),
                mskCursor,
                msk.getBackgroundValue(),
                mskCursor.getSize());
    }

}
