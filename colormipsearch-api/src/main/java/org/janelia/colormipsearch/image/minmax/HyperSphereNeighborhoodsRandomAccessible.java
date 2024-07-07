package org.janelia.colormipsearch.image.minmax;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.PixelHistogram;

public class HyperSphereNeighborhoodsRandomAccessible<T> extends AbstractEuclideanSpace
        implements RandomAccessible<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final long[] radii;
    private final PixelHistogram<T> pixelHistogram;

    public HyperSphereNeighborhoodsRandomAccessible(RandomAccessible<T> source,
                                                    long[] radii,
                                                    PixelHistogram<T> pixelHistogram) {
        super(source.numDimensions());
        this.source = source;
        this.radii = radii;
        this.pixelHistogram = pixelHistogram;
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> randomAccess() {
        return new HyperSphereNeighborhoodsRandomAccess<>(source, radii, pixelHistogram);
    }

    @Override
    public HyperSphereNeighborhoodsRandomAccess<T> randomAccess(Interval interval) {
        return new HyperSphereNeighborhoodsRandomAccess<>(source, radii, pixelHistogram, interval);
    }
}
