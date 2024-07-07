package org.janelia.colormipsearch.image;

import java.util.Arrays;
import java.util.Comparator;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.Type;

public class MIPProjectionRandomAccess<T extends Type<T>> extends AbstractRandomAccessWrapper<T> {

    private final Comparator<T> pixelComparator;
    private final long[] thisAccessPos;
    private final long[] sourceAccessPos;
    private final int axis;
    private final long minAxis;
    private final long maxAxis;

    public MIPProjectionRandomAccess(RandomAccess<T> source,
                                     Comparator<T> pixelComparator,
                                     int axis, long minAxis, long maxAxis) {
        super(source);
        assert axis < source.numDimensions();
        this.pixelComparator = pixelComparator;
        this.axis = axis;
        this.minAxis = minAxis;
        this.maxAxis = maxAxis;
        thisAccessPos = new long[source.numDimensions() - 1];
        sourceAccessPos = new long[source.numDimensions()];
    }

    private MIPProjectionRandomAccess(MIPProjectionRandomAccess<T> c) {
        super(c.source.copy());
        this.pixelComparator = c.pixelComparator;
        this.axis = c.axis;
        this.minAxis = c.minAxis;
        this.maxAxis = c.maxAxis;
        this.thisAccessPos = Arrays.copyOf(c.thisAccessPos, c.thisAccessPos.length);
        this.sourceAccessPos = Arrays.copyOf(c.sourceAccessPos, c.sourceAccessPos.length);
    }

    @Override
    public T get() {
        for (int d = 0; d < sourceAccessPos.length; d++) {
            if (d < axis) {
                sourceAccessPos[d] = thisAccessPos[d];
            } else if (d == axis) {
                sourceAccessPos[d] = minAxis;
            } else {
                sourceAccessPos[d] = thisAccessPos[d - 1];
            }
        }
        source.setPosition(sourceAccessPos);
        T p = source.get().copy();
        for (long pos = minAxis+1; pos < maxAxis; pos++) {
            T curr = source.get();
            if (pixelComparator.compare(p, curr) < 0) {
                p.set(curr);
            }
            source.fwd(axis);
        }
        return p;
    }

    @Override
    public void fwd(final int d) {
        ++thisAccessPos[d];
    }

    @Override
    public void bck(final int d) {
        --thisAccessPos[d];
    }

    @Override
    public void move(final int distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final long distance, final int d) {
        thisAccessPos[d] += distance;
    }

    @Override
    public void move(final Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            move(localizable.getLongPosition(d), d);
        }
    }

    @Override
    public void move(final int[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }

    @Override
    public void move(final long[] distance) {
        for (int d = 0; d < distance.length; d++) {
            move(distance[d], d);
        }
    }


    @Override
    public void setPosition(Localizable localizable) {
        for (int d = 0; d < localizable.numDimensions(); d++) {
            thisAccessPos[d] = localizable.getLongPosition(d);
        }
    }

    @Override
    public void setPosition(int[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(long[] position) {
        for (int d = 0; d < position.length; d++) {
            thisAccessPos[d] = position[d];
        }
    }

    @Override
    public void setPosition(int position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        thisAccessPos[d] = position;
    }

    @Override
    public MIPProjectionRandomAccess<T> copy() {
        return new MIPProjectionRandomAccess<>(this);
    }

    @Override
    public int numDimensions() {
        return super.numDimensions() - 1;
    }
}
