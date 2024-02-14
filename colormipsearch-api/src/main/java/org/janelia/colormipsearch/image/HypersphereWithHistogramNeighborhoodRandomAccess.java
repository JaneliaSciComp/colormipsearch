package org.janelia.colormipsearch.image;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public final class HypersphereWithHistogramNeighborhoodRandomAccess<T> extends AbstractEuclideanSpace implements RandomAccess<Neighborhood<T>> {
    private final RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess;
    private final RandomAccess<T> sourceAccess;
    private final PixelHistogram<T> histogram;
    private final HyperSphereNeighborhoodFactory<T> neighborhoodFactory;
    private final Neighborhood<T> currentNeighborhood;
    private final HyperSphereRegion hypersphereRegion;

    private class HyperSphereRegion {
        final long radius;
        // region center
        final long[] center;
        // the current radius in each dimension we are at
        final double[] r;
        // the current radius in each dimension truncated to long
        final long[] ri;
        // the remaining number of steps in each dimension we still have to go
        final long[] s;

        HyperSphereRegion(long radius, int numDimensions) {
            this.radius = radius;
            this.center = new long[numDimensions];
            this.r = new double[numDimensions];
            this.ri = new long[numDimensions];
            this.s = new long[numDimensions];
        }

        HyperSphereRegion(HyperSphereRegion c) {
            this.radius = c.radius;
            this.center = c.center.clone();
            this.r = c.r.clone();
            this.ri = c.ri.clone();
            this.s = c.s.clone();
        }

        HyperSphereRegion copy() {
            return new HyperSphereRegion(this);
        }

        void fwd(int d) {
            // !!!!
        }
    }

    public HypersphereWithHistogramNeighborhoodRandomAccess(RandomAccess<Neighborhood<T>> sourceNeighborhoodAccess,
                                                            RandomAccess<T> sourceAccess,
                                                            PixelHistogram<T> histogram,
                                                            long radius) {
        super(sourceAccess.numDimensions());
        this.sourceNeighborhoodAccess = sourceNeighborhoodAccess;
        this.sourceAccess = sourceAccess;
        this.histogram = histogram;
        this.hypersphereRegion = new HyperSphereRegion(radius, numDimensions());
        this.neighborhoodFactory = HyperSphereNeighborhood.factory();
        this.currentNeighborhood = this.neighborhoodFactory.create(hypersphereRegion.center, hypersphereRegion.radius, sourceAccess);
    }

    private HypersphereWithHistogramNeighborhoodRandomAccess(final HypersphereWithHistogramNeighborhoodRandomAccess<T> c) {
        super(c.numDimensions());
        this.sourceNeighborhoodAccess = c.sourceNeighborhoodAccess.copy();
        this.sourceAccess = c.sourceAccess.copy();
        this.histogram = c.histogram.copy();
        this.hypersphereRegion = c.hypersphereRegion.copy();
        this.neighborhoodFactory = c.neighborhoodFactory;
        this.currentNeighborhood = this.neighborhoodFactory.create(hypersphereRegion.center, hypersphereRegion.radius, sourceAccess);
    }

    @Override
    public HypersphereWithHistogramNeighborhoodRandomAccess<T> copy() {
        return new HypersphereWithHistogramNeighborhoodRandomAccess<>(this);
    }

    @Override
    public long getLongPosition(int d) {
        return sourceNeighborhoodAccess.getLongPosition(d);
    }

    @Override
    public void fwd(int d) {
        hypersphereRegion.fwd(d);

    }

    @Override
    public void bck(int d) {
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void move(int distance, int d) {
        hypersphereRegion.center[d] += distance;
        // !!!!!!!!!!!!!!!!! FIXME

    }

    @Override
    public void move(long distance, int d) {
        hypersphereRegion.center[d] += distance;
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void move(Localizable distance) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] += distance.getLongPosition(d);
        }
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void move(int[] distance) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] += distance[d];
        }
        // !!!!!!!!!!!!!!!!! FIXME

    }

    @Override
    public void move(long[] distance) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] += distance[d];
        }
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void setPosition(Localizable position) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] = position.getLongPosition(d);
        }
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void setPosition(int[] position) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] = position[d];
        }
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void setPosition(long[] position) {
        for ( int d = 0; d < n; ++d ) {
            hypersphereRegion.center[d] = position[d];
        }
        // !!!!!!!!!!!!!!!!! FIXME
    }

    @Override
    public void setPosition(int position, int d) {
        hypersphereRegion.center[d] = position;
        // !!!!!!!!!!!!!!!!! FIXME

    }

    @Override
    public void setPosition(long position, int d) {
        hypersphereRegion.center[d] = position;
        // !!!!!!!!!!!!!!!!! FIXME

    }

    @Override
    public Neighborhood<T> get() {
        return currentNeighborhood;
    }
}
