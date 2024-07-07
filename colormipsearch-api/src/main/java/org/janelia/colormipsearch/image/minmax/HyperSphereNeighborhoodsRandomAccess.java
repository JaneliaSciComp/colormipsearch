package org.janelia.colormipsearch.image.minmax;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

public final class HyperSphereNeighborhoodsRandomAccess<T> extends AbstractHyperShereNeighborhoodsSampler<T> implements RandomAccess<Neighborhood<T>> {

    public HyperSphereNeighborhoodsRandomAccess(RandomAccessible<T> source,
                                                long[] radii,
                                                PixelHistogram<T> pixelHistogram) {
        super(source, radii, pixelHistogram, null);
    }

    public HyperSphereNeighborhoodsRandomAccess(RandomAccessible<T> source,
                                                long[] radii,
                                                PixelHistogram<T> pixelHistogram,
                                                Interval accessInterval) {
        super(source, radii, pixelHistogram, accessInterval);
    }

    private HyperSphereNeighborhoodsRandomAccess(final HyperSphereNeighborhoodsRandomAccess<T> c) {
        super(c);
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> copy() {
        return new HyperSphereNeighborhoodsRandomAccess<>(this);
    }

}
