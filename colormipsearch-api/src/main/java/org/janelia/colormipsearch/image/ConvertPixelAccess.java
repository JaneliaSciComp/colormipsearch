package org.janelia.colormipsearch.image;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public class ConvertPixelAccess<S, T> extends AbstractIterablePositionableAccess<T> {

    private final RandomAccess<S> source;
    private final PixelConverter<S, T> pixelConverter;

    public ConvertPixelAccess(RandomAccess<S> source, Interval interval, PixelConverter<S, T> pixelConverter) {
        this(source, new RectCoordsHelper(interval), pixelConverter);
    }

    private ConvertPixelAccess(RandomAccess<S> source, RectCoordsHelper coordsHelper, PixelConverter<S, T> pixelConverter) {
        super(coordsHelper);
        this.source = source;
        this.pixelConverter = pixelConverter;
    }

    @Override
    public T get() {
        super.localize(tmpPos);
        return pixelConverter.convertTo(source.setPositionAndGet(tmpPos));
    }

    @Override
    public ConvertPixelAccess<S, T> copy() {
        return new ConvertPixelAccess<>(source.copy(), coordsHelper.copy(), pixelConverter);
    }
}
