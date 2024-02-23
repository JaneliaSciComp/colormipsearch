package org.janelia.colormipsearch.image.minmax;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

public final class HyperSphereNeighborhoodsRandomAccess<T> extends AbstractHyperShereNeighborhoodsSampler<T> implements RandomAccess<Neighborhood<T>> {

    public HyperSphereNeighborhoodsRandomAccess(RandomAccessible<T> source,
                                                int radius,
                                                PixelHistogram<T> pixelHistogram) {
        super(source, radius, pixelHistogram, null);
    }

    public HyperSphereNeighborhoodsRandomAccess(RandomAccessible<T> source,
                                                int radius,
                                                PixelHistogram<T> pixelHistogram,
                                                Interval accessInterval) {
        super(source, radius, pixelHistogram, accessInterval);
    }

    private HyperSphereNeighborhoodsRandomAccess(final HyperSphereNeighborhoodsRandomAccess<T> c) {
        super(c);
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> copy() {
        return new HyperSphereNeighborhoodsRandomAccess<>(this);
    }

}
