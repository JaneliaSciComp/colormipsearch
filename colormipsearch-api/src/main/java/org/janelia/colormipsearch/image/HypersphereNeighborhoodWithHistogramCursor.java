package org.janelia.colormipsearch.image;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public final class HypersphereNeighborhoodWithHistogramCursor<T> extends AbstractEuclideanSpace implements Cursor<T> {
    private final RandomAccess<T> source;
    private final PixelHistogram<T> histogram;
    private Neighborhood<T> neighborhood;
    private final long radius;
    private final int maxDim;
    // the current radius in each dimension we are at
    private final double[] r;
    // the current radius in each dimension truncated to long
    private final long[] ri;
    // the remaining number of steps in each dimension we still have to go
    private final long[] s;

    public HypersphereNeighborhoodWithHistogramCursor(final RandomAccess<T> source, PixelHistogram<T> histogram, long radius) {
        super(source.numDimensions());
        this.source = source;
        this.histogram = histogram;

        this.radius = radius;
        this.maxDim = n - 1;
        r = new double[n];
        ri = new long[n];
        s = new long[n];
    }

    protected HypersphereNeighborhoodWithHistogramCursor(final HypersphereNeighborhoodWithHistogramCursor c) {
        super(c.numDimensions());
        source = c.source.copy();
        this.histogram = c.histogram.copy();

        this.radius = c.radius;
        this.maxDim = c.maxDim;
        r = c.r.clone();
        ri = c.ri.clone();
        s = c.s.clone();
    }

    void updateNeighborhood(Neighborhood<T> neighborhood) {
        this.neighborhood = neighborhood;
        reset();
    }

    @Override
    public T get() {
        return source.get();
    }

    @Override
    public void fwd() {

        if (--s[0] >= 0) {
            source.fwd(0);
        } else {
            int d = 1;
            for (; d < n; ++d) {
                if (--s[d] >= 0) {
                    source.fwd(d);
                    break;
                }
            }

            for (; d > 0; --d) {
                final int e = d - 1;
                final double rd = r[d];
                final long pd = s[d] - ri[d];

                final double rad = Math.sqrt(rd * rd - pd * pd);
                final long radi = (long) rad;
                r[e] = rad;
                ri[e] = radi;
                s[e] = 2 * radi;

                source.setPosition(neighborhood.getLongPosition(e) - radi, e);
            }
        }
    }

    @Override
    public void jumpFwd(final long steps) {
        for (long i = 0; i < steps; ++i)
            fwd();
    }

    @Override
    public T next() {
        fwd();
        return get();
    }

    @Override
    public void remove() {
        // NB: no action.
    }

    @Override
    public void reset() {
        histogram.clear();
        for (int d = 0; d < maxDim; ++d) {
            r[d] = ri[d] = s[d] = 0;
            source.setPosition(neighborhood.getLongPosition(d), d);
        }
        source.setPosition(neighborhood.getLongPosition(maxDim) - radius - 1, maxDim);
        r[maxDim] = radius;
        ri[maxDim] = radius;
        s[maxDim] = 1 + 2 * radius;
    }

    @Override
    public boolean hasNext() {
        return s[maxDim] > 0;
    }

    @Override
    public float getFloatPosition(final int d) {
        return source.getFloatPosition(d);
    }

    @Override
    public double getDoublePosition(final int d) {
        return source.getDoublePosition(d);
    }

    @Override
    public int getIntPosition(final int d) {
        return source.getIntPosition(d);
    }

    @Override
    public long getLongPosition(final int d) {
        return source.getLongPosition(d);
    }

    @Override
    public void localize(final long[] position) {
        source.localize(position);
    }

    @Override
    public void localize(final float[] position) {
        source.localize(position);
    }

    @Override
    public void localize(final double[] position) {
        source.localize(position);
    }

    @Override
    public void localize(final int[] position) {
        source.localize(position);
    }

    @Override
    public HypersphereNeighborhoodWithHistogramCursor copy() {
        return new HypersphereNeighborhoodWithHistogramCursor(this);
    }
}
