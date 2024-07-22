package org.janelia.colormipsearch.image;

import java.util.Comparator;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.Type;
import net.imglib2.util.Intervals;

public class MaxFilter1dRandomAccess<T extends Type<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    private final Interval sourceDataInterval;
    private final long[] tmpCoords;
    private final Max<T> maxOf;
    private final T pxType;
    private final int radius;
    private final int axis;

    MaxFilter1dRandomAccess(RandomAccess<T> source,
                            Interval sourceDataInterval,
                            Max<T> maxOf,
                            int radius,
                            int axis) {
        super(source, source.numDimensions());
        this.sourceDataInterval = sourceDataInterval;
        this.maxOf = maxOf;
        this.radius = radius;
        this.axis = axis;
        this.tmpCoords = thisAccessPos.clone();
        this.pxType = source.get().createVariable();
    }

    private MaxFilter1dRandomAccess(MaxFilter1dRandomAccess<T> c) {
        super(c);
        this.sourceDataInterval = c.sourceDataInterval;
        this.maxOf = c.maxOf;
        this.radius = c.radius;
        this.axis = c.axis;
        this.tmpCoords = c.tmpCoords.clone();
        this.pxType = c.pxType.createVariable();
    }

    @Override
    public T get() {
        source.setPosition(thisAccessPos);
        int minRadius = (int) Math.min(Math.abs(thisAccessPos[axis] - sourceDataInterval.min(axis)), radius);
        int maxRadius = (int) Math.min(Math.abs(sourceDataInterval.max(axis) - thisAccessPos[axis]), radius);
        source.move(-minRadius, axis);
        pxType.set(source.get());
        for (int r = -minRadius; r  <= maxRadius; r++) {
            T rpx = source.get();
            maxOf.maxOf(pxType, rpx, pxType);
            source.fwd(axis);
        }
        return pxType;
    }

    @Override
    public MaxFilter1dRandomAccess<T> copy() {
        return new MaxFilter1dRandomAccess<T>(this);
    }

}
