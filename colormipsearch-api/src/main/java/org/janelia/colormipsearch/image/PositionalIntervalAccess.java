package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class PositionalIntervalAccess<T> extends AbstractIterablePositionableAccess<T> {

    private final RandomAccess<T> sourceAccess;

    public PositionalIntervalAccess(RandomAccess<T> sourceAccess, Interval interval) {
        this(sourceAccess, new RectCoordsHelper(interval));
    }

    private PositionalIntervalAccess(RandomAccess<T> sourceAccess, RectCoordsHelper coordsHelper) {
        super(coordsHelper);
        this.sourceAccess = sourceAccess;
    }

    @Override
    public PositionalIntervalAccess<T> copy() {
        return new PositionalIntervalAccess<>(sourceAccess.copy(), coordsHelper.copy());
    }

    @Override
    public T get() {
        // read the current pos in tmpPos
        coordsHelper.currentPos(tmpPos);
        return sourceAccess.setPositionAndGet(tmpPos);
    }
}
