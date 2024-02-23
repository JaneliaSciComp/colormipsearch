package org.janelia.colormipsearch.image.minmax;

import java.util.Arrays;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import org.janelia.colormipsearch.image.PixelHistogram;

public class HyperSphereShape implements Shape {

    private final long radius;
    private final PixelHistogram<?> pixelHistogram;

    public HyperSphereShape(long radius, PixelHistogram<?> pixelHistogram) {
        assert radius != 0; // the current minmax does not work when radius == 0
        this.radius = radius < 0 ? -radius : radius;
        this.pixelHistogram = pixelHistogram;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> IterableInterval<Neighborhood<T>> neighborhoods(RandomAccessibleInterval<T> source) {
        return new HypersphereNeighborhoodsIterableInterval<T>(
                source,
                radius,
                (PixelHistogram<T>) pixelHistogram
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> RandomAccessible<Neighborhood<T>> neighborhoodsRandomAccessible(RandomAccessible<T> source) {
        return new HyperSphereNeighborhoodsRandomAccessible<T>(
                source,
                radius,
                (PixelHistogram<T>) pixelHistogram
        );
    }

    @Override
    public <T> IterableInterval<Neighborhood<T>> neighborhoodsSafe(RandomAccessibleInterval<T> source) {
        return neighborhoods(source);
    }

    @Override
    public <T> RandomAccessible<Neighborhood<T>> neighborhoodsRandomAccessibleSafe(RandomAccessible<T> source) {
        return neighborhoodsRandomAccessible(source);
    }

    @Override
    public Interval getStructuringElementBoundingBox(int numDimensions) {
        final long[] min = new long[numDimensions];
        Arrays.fill(min, -radius);

        final long[] max = new long[numDimensions];
        Arrays.fill(max, radius);

        return new FinalInterval(min, max);
    }

}
