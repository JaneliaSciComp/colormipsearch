package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Comparator;

import net.imglib2.RandomAccess;
import net.imglib2.type.Type;

public class MIPProjectionRandomAccess<T extends Type<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    private final Comparator<T> pixelComparator;
    private final long[] sourceAccessPos;
    private final int projection;
    private final long minAxis;
    private final long maxAxis;

    public MIPProjectionRandomAccess(RandomAccess<T> source,
                                     Comparator<T> pixelComparator,
                                     int projection,
                                     long minAxis, long maxAxis) {
        super(source, source.numDimensions() - 1);
        assert projection < source.numDimensions();
        this.pixelComparator = pixelComparator;
        this.projection = projection;
        this.minAxis = minAxis;
        this.maxAxis = maxAxis;
        sourceAccessPos = new long[source.numDimensions()];
    }

    private MIPProjectionRandomAccess(MIPProjectionRandomAccess<T> c) {
        super(c);
        this.pixelComparator = c.pixelComparator;
        this.projection = c.projection;
        this.minAxis = c.minAxis;
        this.maxAxis = c.maxAxis;
        this.sourceAccessPos = Arrays.copyOf(c.sourceAccessPos, c.sourceAccessPos.length);
    }

    @Override
    public T get() {
        for (int d = 0; d < sourceAccessPos.length; d++) {
            if (d < projection) {
                sourceAccessPos[d] = thisAccessPos[d];
            } else if (d == projection) {
                sourceAccessPos[d] = minAxis;
            } else {
                sourceAccessPos[d] = thisAccessPos[d - 1];
            }
        }
        source.setPosition(sourceAccessPos);
        T targetPx = source.get().copy();
        for (long pos = minAxis + 1; pos < maxAxis; pos++) {
            T curr = source.get();
            if (pixelComparator.compare(targetPx, curr) < 0) {
                targetPx.set(curr);
            }
            source.fwd(projection);
        }
        return targetPx;
    }

    @Override
    public MIPProjectionRandomAccess<T> copy() {
        return new MIPProjectionRandomAccess<>(this);
    }
}
