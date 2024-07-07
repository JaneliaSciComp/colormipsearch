package org.janelia.colormipsearch.image.minmax;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.AbstractLocalizable;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperSphereNeighborhood<T> extends AbstractLocalizable implements Neighborhood<T> {

    private final HyperSphereRegion neighborhoodRegion;
    private final RandomAccessible<T> sourceAccess;
    private final int maxDim;
    private long size;

    public final class LocalCursor extends AbstractEuclideanSpace implements Cursor<T> {
        private final RandomAccess<T> source;

        // the current radius in each dimension we are at
        private final double[] r;

        // the current radius in each dimension truncated to long
        private final long[] ri;

        // the remaining number of steps in each dimension we still have to go
        private final long[] s;

        public LocalCursor(final RandomAccess<T> source) {
            super(source.numDimensions());
            this.source = source;
            r = new double[n];
            ri = new long[n];
            s = new long[n];
            reset();
        }

        private LocalCursor(LocalCursor c) {
            super(c.numDimensions());
            source = c.source.copy();
            r = c.r.clone();
            ri = c.ri.clone();
            s = c.s.clone();
        }

        @Override
        public T get() {
            return source.get();
        }

        @Override
        public void fwd() {
            if (--s[0] >= 0)
                source.fwd(0);
            else {
                int d = 1;
                for (; d < n; ++d) {
                    if (--s[d] >= 0) {
                        source.fwd(d);
                        break;
                    }
                }

                for (; d > 0; --d) {
                    final int e = d - 1;
                    final double rd = r[d];
                    final long pd = s[d] - ri[d];

                    final double rad = Math.sqrt(rd * rd - pd * pd);
                    final long radi = (long) rad;
                    r[e] = rad;
                    ri[e] = radi;
                    s[e] = 2 * radi;

                    source.setPosition(position[e] - radi, e);
                }
            }
        }

        @Override
        public void jumpFwd(final long steps) {
            for (long i = 0; i < steps; ++i)
                fwd();
        }

        @Override
        public T next() {
            fwd();
            return get();
        }

        @Override
        public void remove() {
            // NB: no action.
        }

        @Override
        public void reset() {
            for (int d = 0; d < maxDim; ++d) {
                r[d] = ri[d] = s[d] = 0;
                source.setPosition(position[d], d);
            }

            source.setPosition(position[maxDim] - neighborhoodRegion.radii[maxDim] - 1, maxDim);

            r[maxDim] = neighborhoodRegion.radii[maxDim];
            ri[maxDim] = neighborhoodRegion.radii[maxDim];
            s[maxDim] = 1 + 2 * neighborhoodRegion.radii[maxDim];
        }

        @Override
        public boolean hasNext() {
            return s[maxDim] > 0;
        }

        @Override
        public float getFloatPosition(final int d) {
            return source.getFloatPosition(d);
        }

        @Override
        public double getDoublePosition(final int d) {
            return source.getDoublePosition(d);
        }

        @Override
        public int getIntPosition(final int d) {
            return source.getIntPosition(d);
        }

        @Override
        public long getLongPosition(final int d) {
            return source.getLongPosition(d);
        }

        @Override
        public void localize(final long[] position) {
            source.localize(position);
        }

        @Override
        public void localize(final float[] position) {
            source.localize(position);
        }

        @Override
        public void localize(final double[] position) {
            source.localize(position);
        }

        @Override
        public void localize(final int[] position) {
            source.localize(position);
        }

        @Override
        public LocalCursor copy() {
            return new LocalCursor(this);
        }
    }

    HyperSphereNeighborhood(HyperSphereRegion neighborhoodRegion, RandomAccessible<T> sourceAccess) {
        super(neighborhoodRegion.center);
        this.neighborhoodRegion = neighborhoodRegion;
        this.sourceAccess = sourceAccess;
        this.maxDim = n - 1;
        this.size = -1;
    }

    @Override
    public Interval getStructuringElementBoundingBox() {
        return new FinalInterval(neighborhoodRegion.min, neighborhoodRegion.max);
    }

    @Override
    public Cursor<T> cursor() {
        return new LocalCursor(sourceAccess.randomAccess(getStructuringElementBoundingBox()));
    }

    @Override
    public Cursor<T> localizingCursor() {
        return cursor();
    }

    @Override
    public long size() {
        if (this.size == -1) {
            this.size = neighborhoodRegion.computeSize();
        }
        return size;
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
    public int numDimensions() {
        return neighborhoodRegion.numDimensions();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("neighborhoodRegion", neighborhoodRegion)
                .toString();
    }
}
