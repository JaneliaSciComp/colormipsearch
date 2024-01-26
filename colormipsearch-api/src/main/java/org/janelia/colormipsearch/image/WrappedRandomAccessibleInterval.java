package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.AbstractConvertedRandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccessibleInterval;

public class WrappedRandomAccessibleInterval<T> extends AbstractConvertedRandomAccessibleInterval<T, T> {

    private final WrappedRandomAccess<T> access;

    public WrappedRandomAccessibleInterval(RandomAccessibleInterval<T> source) {
        super(source);
        this.access = new WrappedRandomAccess<>(source.randomAccess());
    }

    @Override
    public WrappedRandomAccess<T> randomAccess() {
        return access;
    }

    @Override
    public WrappedRandomAccess<T> randomAccess(Interval interval) {
        return access;
    }
}
