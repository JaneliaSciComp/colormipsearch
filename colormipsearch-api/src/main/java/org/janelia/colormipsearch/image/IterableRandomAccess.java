package org.janelia.colormipsearch.image;

import net.imglib2.RandomAccess;

public interface IterableRandomAccess<T> extends RandomAccess<T>, CursorLocalizableSampler<T> {
    IterableRandomAccess<T> copy();
    long getSize();
}
