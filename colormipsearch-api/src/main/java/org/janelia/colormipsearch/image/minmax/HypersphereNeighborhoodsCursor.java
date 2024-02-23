package org.janelia.colormipsearch.image.minmax;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

class HypersphereNeighborhoodsCursor<T> extends AbstractHyperShereNeighborhoodsSampler<T> implements Cursor<Neighborhood<T>> {

    private long index;
    private final long maxIndex;

    /**
     * Main constructor.
     */
    public HypersphereNeighborhoodsCursor(RandomAccessibleInterval<T> source,
                                          long radius,
                                          PixelHistogram<T> pixelHistogram) {
        super(source, radius, pixelHistogram, null);
        long[] dimensions = new long[n];
        source.dimensions(dimensions);
        long size = dimensions[ 0 ];
        for ( int d = 1; d < n; ++d )
            size *= dimensions[d];
        maxIndex = size - 1;
        reset();
    }

    /**
     * Copy constructor.
     * @param c
     */
    private HypersphereNeighborhoodsCursor(HypersphereNeighborhoodsCursor<T> c) {
        super(c);
        maxIndex = c.maxIndex;
        index = c.index;
    }

    @Override
    public HypersphereNeighborhoodsCursor<T> copy() {
        return new HypersphereNeighborhoodsCursor<>(this);
    }

    @Override
    public void fwd() {
        super.fwd(1);
    }

    @Override
    public void reset() {
        index = -1;
        super.resetCurrentPos();
    }

    @Override
    public boolean hasNext() {
        return index < maxIndex;
    }

}
