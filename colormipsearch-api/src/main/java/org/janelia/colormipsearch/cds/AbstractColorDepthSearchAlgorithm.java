package org.janelia.colormipsearch.cds;

import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.stream.Streams;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.MaskedPixelAccess;
import org.janelia.colormipsearch.image.MaskedPixelCursor;
import org.janelia.colormipsearch.image.PixelPositionsListCursor;
import org.janelia.colormipsearch.image.RectIntervalHelper;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> implements ColorDepthSearchAlgorithm<S> {

    private static class QueryAccess<T> extends SimpleImageAccess<T> {
        private final Cursor<T> cursor;
        private final long size;

        QueryAccess(RandomAccessibleInterval<T> source,
                    Cursor<T> cursor,
                    T backgroundValue,
                    long size) {
            super(source, backgroundValue);
            this.cursor = cursor;
            this.size = size;
        }

        @Override
        public Cursor<T> cursor() {
            cursor.reset();
            return cursor;
        }

        @Override
        public Cursor<T> localizingCursor() {
            cursor.reset();
            return cursor;
        }

        @Override
        public long size() {
            return size;
        }
    }

    private final ImageAccess<? extends RGBPixelType<?>> queryImage;
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

    public long getQuerySize() {
        return queryImage.size();
    }

    private ImageAccess<? extends RGBPixelType<?>> getMaskPosArray(ImageAccess<RGBPixelType<?>> msk,
                                                                   int thresm) {
        BiPredicate<long[], RGBPixelType<?>> isRGBBelowThreshold = (long[] pos, RGBPixelType<?> pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= thresm && g <= thresm && b <= thresm;
        };
        RandomAccess<RGBPixelType<?>> mskAccess = new MaskedPixelAccess<>(
                msk.randomAccess(),
                isRGBBelowThreshold,
                msk.getBackgroundValue()
        );
        ImageAccess<RGBPixelType<?>> thresholdMaskedAccess = new SimpleImageAccess<>(
                mskAccess,
                msk,
                msk.getBackgroundValue()
        );
        RectIntervalHelper rectIntervalHelperHelper = new RectIntervalHelper(thresholdMaskedAccess);
        long[] tmpPos = new long[rectIntervalHelperHelper.numDimensions()];
        int[] pixelPositions = ImageAccessUtils.stream(thresholdMaskedAccess.cursor())
                .filter(pos -> !pos.get().isZero())
                .mapToInt(pos -> {
                    pos.localize(tmpPos);
                    return rectIntervalHelperHelper.rectCoordsToIntLinearIndex(tmpPos);
                })
                .toArray();
        // get mask shape
        msk.dimensions(tmpPos);
        PixelPositionsListCursor<RGBPixelType<?>> mskPixelsCursor = new PixelPositionsListCursor<>(
                mskAccess, tmpPos, pixelPositions
        );
        return new QueryAccess<>(
                thresholdMaskedAccess,
                mskPixelsCursor,
                thresholdMaskedAccess.getBackgroundValue(),
                mskPixelsCursor.getSize());
    }

}
