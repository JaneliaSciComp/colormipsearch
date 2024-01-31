package org.janelia.colormipsearch.image;

import net.imglib2.RandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccess;

public class ConvertPixelAccess<S, T> extends AbstractConvertedRandomAccess<S, T> {

    private final PixelConverter<S, T> pixelConverter;

    public ConvertPixelAccess(RandomAccess<S> source, PixelConverter<S, T> pixelConverter) {
        super(source);
        this.pixelConverter = pixelConverter;
    }

    @Override
    public T get() {
        S sourcePixel = source.get();
        return pixelConverter.convert(sourcePixel);
    }

    @Override
    public ConvertPixelAccess<S, T> copy() {
        return new ConvertPixelAccess<>(source.copy(), pixelConverter);
    }
}
