package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.LocalizableSampler;
import net.imglib2.RandomAccess;

public class PixelPositionsListCursor<T> implements Cursor<T>, LocalizableSampler<T> {

    private final RandomAccess<T> sourceAccess;
    private final RectIntervalHelper coordsHelper;
    private final int[] pixelPositions;
    private int currentPixelIndex;

    public PixelPositionsListCursor(RandomAccess<T> sourceAccess, long[] shape, int[] pixelPositions) {
        this(sourceAccess, new RectIntervalHelper(shape), pixelPositions, -1);
    }

    private PixelPositionsListCursor(RandomAccess<T> sourceAccess,
                                     RectIntervalHelper coordsHelper,
                                     int[] pixelPositions,
                                     int currentPixelIndex) {
        this.sourceAccess = sourceAccess;
        this.coordsHelper = coordsHelper;
        this.pixelPositions = pixelPositions;
        this.currentPixelIndex = currentPixelIndex;
    }

    @Override
    public PixelPositionsListCursor<T> copy() {
        return new PixelPositionsListCursor<>(
                sourceAccess.copy(), coordsHelper.copy(), pixelPositions, currentPixelIndex
        );
    }

    @Override
    public void fwd() {
        currentPixelIndex++;
    }

    @Override
    public void reset() {
        currentPixelIndex = -1;
    }

    @Override
    public boolean hasNext() {
        return currentPixelIndex < pixelPositions.length;
    }

    @Override
    public void localize(long[] position) {
        coordsHelper.unsafeLinearIndexToRectCoords(pixelPositions[currentPixelIndex], position);
    }

    @Override
    public long getLongPosition(int d) {
        return coordsHelper.unsafeLinearIndexToRectCoord(pixelPositions[currentPixelIndex], d);
    }

    @Override
    public int numDimensions() {
        return sourceAccess.numDimensions();
    }

    @Override
    public T get() {
        return sourceAccess.setPositionAndGet(
                coordsHelper.linearIndexToRectCoords(pixelPositions[currentPixelIndex])
        );
    }

    public long getSize() {
        return pixelPositions.length;
    }
}
