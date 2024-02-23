package org.janelia.colormipsearch.image.minmax;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

public class HyperSphereNeighborhoodsRandomAccessible<T> extends AbstractEuclideanSpace
        implements RandomAccessible<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final int radius;
    private final PixelHistogram<T> pixelHistogram;

    public HyperSphereNeighborhoodsRandomAccessible(RandomAccessible<T> source,
                                                    int radius,
                                                    PixelHistogram<T> pixelHistogram) {
        super(source.numDimensions());
        this.source = source;
        this.radius = radius;
        this.pixelHistogram = pixelHistogram;
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> randomAccess() {
        return new HyperSphereNeighborhoodsRandomAccess<>(source, radius, pixelHistogram);
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> randomAccess(Interval interval) {
        return new HyperSphereNeighborhoodsRandomAccess<>(source, radius, pixelHistogram, interval);
    }
}
