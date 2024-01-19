package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;

public class CursorLocalizableSamplerAdapter<T> implements CursorLocalizableSampler<T> {

    private final Cursor<T> sourceCursor;

    public CursorLocalizableSamplerAdapter(Cursor<T> sourceCursor) {
        this.sourceCursor = sourceCursor;
    }

    @Override
    public T get() {
        return sourceCursor.get();
    }

    @Override
    public CursorLocalizableSamplerAdapter<T> copy() {
        return new CursorLocalizableSamplerAdapter<>(sourceCursor.copy());
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
