package org.janelia.colormipsearch.image.minmax;

import net.imglib2.AbstractLocalizable;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public class HyperSphereNeighborhood<T> extends AbstractLocalizable implements Neighborhood<T> {

    private final HyperSphereRegion neighborhoodRegion;

    HyperSphereNeighborhood(HyperSphereRegion neighborhoodRegion, RandomAccess<T> sourceAccess) {
        super(neighborhoodRegion.center);
        this.neighborhoodRegion = neighborhoodRegion;
    }

    @Override
    public Interval getStructuringElementBoundingBox() {
        return new FinalInterval(neighborhoodRegion.min, neighborhoodRegion.max);
    }

    @Override
    public Cursor<T> cursor() {
        return null; // !!!!!! FIXME
    }

    @Override
    public Cursor<T> localizingCursor() {
        return null; // !!!!! FIXME
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public Object iterationOrder() {
        return neighborhoodRegion; // iteration order is compatible only with a hypershpere
    }

    @Override
    public long min(int d) {
        return neighborhoodRegion.min[d];
    }

    @Override
    public long max(int d) {
        return neighborhoodRegion.max[d];
    }

    @Override
    public long getLongPosition(int d) {
        return neighborhoodRegion.center[d];
    }

    @Override
    public int numDimensions() {
        return neighborhoodRegion.numDimensions();
    }
}
