package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.LocalizableSampler;

public interface CursorLocalizableSampler<T> extends Cursor<T>, LocalizableSampler<T> {
    CursorLocalizableSampler<T> copy();
}
