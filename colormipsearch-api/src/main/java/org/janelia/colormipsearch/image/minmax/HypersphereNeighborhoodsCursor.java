package org.janelia.colormipsearch.image.minmax;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodCursor;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

class HypersphereNeighborhoodsCursor<T> extends AbstractHyperShereNeighborhoodsSampler<T> implements Cursor<Neighborhood<T>> {

    /**
     * Main constructor.
     */
    public HypersphereNeighborhoodsCursor(RandomAccessible<T> source,
                                          long radius,
                                          PixelHistogram<T> pixelHistogram) {
        super(source, radius, pixelHistogram, null);
    }

    /**
     * Copy constructor.
     * @param c
     */
    private HypersphereNeighborhoodsCursor(HypersphereNeighborhoodsCursor<T> c) {
        super(c);
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
        // !!!!! FIXME
    }

    @Override
    public boolean hasNext() {
        return false; // !!!!! FIXME
    }

    @Override
    public long getLongPosition(int d) {
        return 0; // !!!!!!!!!!! FIXME
    }

    @Override
    public int numDimensions() {
        return 0; // !!!!!!!! FIXME
    }
}
