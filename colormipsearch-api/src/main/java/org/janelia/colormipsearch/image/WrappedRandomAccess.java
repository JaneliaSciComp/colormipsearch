package org.janelia.colormipsearch.image;

import java.util.LinkedHashMap;

import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccess;

public class WrappedRandomAccess<T> extends AbstractConvertedRandomAccess<T, T> {

    private final LinkedHashMap<String, T> pixelCache;
    private final long[] tmpPos;

    public WrappedRandomAccess(RandomAccess<T> source) {
        super(source);
        pixelCache = new LinkedHashMap<>();
        tmpPos = new long[source.numDimensions()];
    }

    @Override
    public T get() {
        localize(tmpPos);
        T v = pixelCache.get(ImageAccessUtils.hashableLocation(tmpPos));
        if (v != null) {
            return v;
        } else {
            return source.get();
        }
    }

    @Override
    public WrappedRandomAccess<T> copy() {
        return new WrappedRandomAccess<>(source.copy());
    }

    public void updateValue(long[] pos, T v) {
        pixelCache.put(ImageAccessUtils.hashableLocation(pos), v);
    }
}
