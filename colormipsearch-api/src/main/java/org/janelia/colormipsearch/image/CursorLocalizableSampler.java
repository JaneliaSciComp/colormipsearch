package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.LocalizableSampler;

public class CursorLocalizableSampler<T> implements Cursor<T>, LocalizableSampler<T> {

    private final Cursor<T> sourceCursor;

    public CursorLocalizableSampler(Cursor<T> sourceCursor) {
        this.sourceCursor = sourceCursor;
    }

    @Override
    public T get() {
        return sourceCursor.get();
    }

    @Override
    public CursorLocalizableSampler<T> copy() {
        return new CursorLocalizableSampler<>(sourceCursor.copy());
    }

    @Override
    public long getLongPosition(int d) {
        return sourceCursor.getLongPosition(d);
    }

    @Override
    public void fwd() {
        sourceCursor.fwd();
    }

    @Override
    public void reset() {
        sourceCursor.reset();
    }

    @Override
    public boolean hasNext() {
        return sourceCursor.hasNext();
    }

    @Override
    public int numDimensions() {
        return sourceCursor.numDimensions();
    }
}
