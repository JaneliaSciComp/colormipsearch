package org.janelia.colormipsearch.image.minmax;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.type.numeric.NumericType;
import org.janelia.colormipsearch.image.PixelHistogram;

public class HyperSphereShape implements Shape {

    private final long[] radii;
    private final PixelHistogram<?> pixelHistogram;

    public HyperSphereShape(long[] radii, PixelHistogram<?> pixelHistogram) {
        // the current minmax assumes forall r in radii; r > 0
        this.radii = radii;
        this.pixelHistogram = pixelHistogram;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> IterableInterval<Neighborhood<T>> neighborhoods(RandomAccessibleInterval<T> source) {
        return new HypersphereNeighborhoodsIterableInterval<T>(
                source,
                radii,
                (PixelHistogram<T>) pixelHistogram
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> RandomAccessible<Neighborhood<T>> neighborhoodsRandomAccessible(RandomAccessible<T> source) {
        return new HyperSphereNeighborhoodsRandomAccessible<T>(
                source,
                radii,
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
        final long[] max = new long[numDimensions];
        for (int d = 0; d < min.length; d++) {
            min[d] = -radii[d];
            max[d] = radii[d];
        }
        return new FinalInterval(min, max);
    }

}
