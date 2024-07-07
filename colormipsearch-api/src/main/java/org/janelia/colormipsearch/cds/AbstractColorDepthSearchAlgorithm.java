package org.janelia.colormipsearch.cds;

import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.PixelPositionsListCursor;
import org.janelia.colormipsearch.image.RectIntervalHelper;
import org.janelia.colormipsearch.image.SimpleImageAccess;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore, P extends RGBPixelType<P>, G extends IntegerType<G>> implements ColorDepthSearchAlgorithm<S, P, G> {

    private static class QueryAccess<P extends Type<P>> extends SimpleImageAccess<P> {
        private final Cursor<P> cursor;
        private final long size;

        QueryAccess(RandomAccessibleInterval<P> source,
                    Cursor<P> cursor,
                    P backgroundValue,
                    long size) {
            super(source, backgroundValue);
            this.cursor = cursor;
            this.size = size;
        }

        @Override
        public Cursor<P> cursor() {
            cursor.reset();
            return cursor;
        }

        @Override
        public Cursor<P> localizingCursor() {
            cursor.reset();
            return cursor;
        }

        @Override
        public long size() {
            return size;
        }
    }

    private final ImageAccess<P> queryImage;
    final int targetThreshold;
    final double zTolerance;

    @SuppressWarnings("unchecked")
    protected AbstractColorDepthSearchAlgorithm(@Nonnull ImageAccess<P> queryImage,
                                                int queryThreshold,
                                                int targetThreshold,
                                                double zTolerance) {
        this.targetThreshold = targetThreshold;
        this.zTolerance = zTolerance;
        this.queryImage = getMaskPosArray(queryImage, queryThreshold);
    }

    @Override
    public ImageAccess<P> getQueryImage() {
        return queryImage;
    }

    public long getQuerySize() {
        return queryImage.size();
    }

    private ImageAccess<P> getMaskPosArray(ImageAccess<P> msk, int thresm) {
        BiPredicate<long[], P> isRGBBelowThreshold = (long[] pos, P pixel) -> {
            int r = pixel.getRed();
            int g = pixel.getGreen();
            int b = pixel.getBlue();
            // mask the pixel if all channels are below the threshold
            return r <= thresm && g <= thresm && b <= thresm;
        };
        ImageAccess<P> thresholdMaskedAccess = ImageTransforms.maskPixelsMatchingCond(
                msk, isRGBBelowThreshold
        );
        RectIntervalHelper rectIntervalHelperHelper = new RectIntervalHelper(thresholdMaskedAccess);
        long[] tmpPos = new long[rectIntervalHelperHelper.numDimensions()];
        int[] pixelPositions = ImageAccessUtils.stream(thresholdMaskedAccess.cursor(), false)
                .filter(pos -> !pos.get().isZero())
                .mapToInt(pos -> {
                    pos.localize(tmpPos);
                    return rectIntervalHelperHelper.rectCoordsToIntLinearIndex(tmpPos);
                })
                .toArray();
        // get mask shape
        msk.dimensions(tmpPos);
        PixelPositionsListCursor<P> mskPixelsCursor = new PixelPositionsListCursor<>(
                thresholdMaskedAccess.randomAccess(), tmpPos, pixelPositions
        );
        return new QueryAccess<>(
                thresholdMaskedAccess,
                mskPixelsCursor,
                thresholdMaskedAccess.getBackgroundValue(),
                mskPixelsCursor.getSize());
    }

}
