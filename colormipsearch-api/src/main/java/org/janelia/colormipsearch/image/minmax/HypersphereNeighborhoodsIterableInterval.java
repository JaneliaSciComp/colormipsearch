package org.janelia.colormipsearch.image.minmax;

import java.util.Iterator;

import net.imglib2.AbstractInterval;
import net.imglib2.Cursor;
import net.imglib2.FlatIterationOrder;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodCursor;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

class HypersphereNeighborhoodsIterableInterval<T> extends AbstractInterval
                                                  implements IterableInterval<Neighborhood<T>> {
    final RandomAccessibleInterval<T> source;
    final long[] radii;
    final PixelHistogram<T> pixelHistogram;
    final long size;

    HypersphereNeighborhoodsIterableInterval(RandomAccessibleInterval<T> source,
                                             long[] radii,
                                             PixelHistogram<T> pixelHistogram) {
        super(source);
        this.source = source;
        this.radii = radii;
        this.pixelHistogram = pixelHistogram;

        long s = source.dimension(0);
        for (int d = 1; d < n; ++d)
            s *= source.dimension(d);
        size = s;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public Neighborhood<T> firstElement() {
        return cursor().next();
    }

    @Override
    public Object iterationOrder() {
        return new FlatIterationOrder(this);
    }

    @Override
    public Iterator<Neighborhood<T>> iterator() {
        return cursor();
    }

    @Override
    public Cursor<Neighborhood<T>> cursor() {
        return new HypersphereNeighborhoodsCursor<>(source, radii, pixelHistogram);
    }

    @Override
    public Cursor<Neighborhood<T>> localizingCursor() {
        return cursor();
    }
}
