package org.janelia.colormipsearch.image.minmax;

import java.util.Arrays;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhoodFactory;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.PixelHistogram;

abstract class AbstractHyperShereNeighborhoodsSampler<T> extends AbstractEuclideanSpace implements Localizable, Positionable, Sampler<Neighborhood<T>> {

    private final RandomAccessible<T> source;
    private final RandomAccess<T> sourceAccess;
    private final Interval sourceInterval;
    private final PixelHistogram<T> pixelHistogram;

    private final HyperSphereRegion currentNeighborhoodRegion;
    private final HyperSphereRegion prevNeighborhoodRegion;
    private final Neighborhood<T> currentNeighborhood;
    private final long[] workingPos;

    AbstractHyperShereNeighborhoodsSampler(RandomAccessible<T> source,
                                           long radius,
                                           PixelHistogram<T> pixelHistogram,
                                           Interval accessInterval) {
        super(source.numDimensions());
        this.source = source;
        this.pixelHistogram = pixelHistogram;
        this.currentNeighborhoodRegion = new HyperSphereRegion(source.numDimensions(), radius);
        this.prevNeighborhoodRegion = new HyperSphereRegion(source.numDimensions(), radius);

        Interval sourceAccessInterval = accessInterval == null && source instanceof Interval ? (Interval) source : accessInterval;
        if (sourceAccessInterval == null) {
            sourceInterval = null;
        } else {
            final long[] accessMin = new long[ n ];
            final long[] accessMax = new long[ n ];
            sourceAccessInterval.min( accessMin );
            sourceAccessInterval.max( accessMax );
            for (int d = 0; d < n; ++d) {
                accessMin[ d ] -= radius;
                accessMax[ d ] += radius;
            }
            sourceInterval = new FinalInterval( accessMin, accessMax );
        }
        this.sourceAccess = sourceInterval == null ? source.randomAccess() : source.randomAccess(sourceInterval);
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, sourceAccess);
        this.workingPos = createWorkingPos(radius);
    }

    AbstractHyperShereNeighborhoodsSampler(AbstractHyperShereNeighborhoodsSampler<T> c) {
        super(c.n);
        this.source = c.source;
        this.pixelHistogram = c.pixelHistogram.copy();
        this.currentNeighborhoodRegion = c.currentNeighborhoodRegion.copy();
        this.prevNeighborhoodRegion = c.prevNeighborhoodRegion.copy();
        this.sourceInterval = c.sourceInterval;
        this.sourceAccess = c.sourceAccess.copy();
        this.currentNeighborhood = new HyperSphereNeighborhood<>(currentNeighborhoodRegion, sourceAccess);
        this.workingPos = c.workingPos.clone();
    }

    private long[] createWorkingPos(long radius) {
        long[] pos = new long[n];
        Arrays.fill(pos, -radius - 1);
        return pos;
    }

    @Override
    public long getLongPosition(int d) {
        return currentNeighborhoodRegion.center[d];
    }

    @Override
    public Neighborhood<T> get() {
        return currentNeighborhood;
    }

    @Override
    public void fwd(int d) {
        updatedPos(d, () -> ++currentNeighborhoodRegion.center[d]);
    }

    @Override
    public void bck(int d) {
        updatedPos(d, () -> --currentNeighborhoodRegion.center[d] );
    }

    @Override
    public void move(int distance, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] += distance);

    }

    @Override
    public void move(long distance, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] += distance);
    }

    @Override
    public void move(Localizable distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(int[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void move(long[] distance) {
        updatedPos(0, () -> CoordUtils.addCoords(currentNeighborhoodRegion.center, distance, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(Localizable position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(int[] position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(long[] position) {
        updatedPos(0, () -> CoordUtils.setCoords(position, currentNeighborhoodRegion.center));
    }

    @Override
    public void setPosition(int position, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] = position);
    }

    @Override
    public void setPosition(long position, int d) {
        updatedPos(d, () -> currentNeighborhoodRegion.center[d] = position);
    }

    private void updatedPos(int axis, Runnable updateAction) {
        prevNeighborhoodRegion.setLocationTo(currentNeighborhoodRegion);
        updateAction.run();
        currentNeighborhoodRegion.updateMinMax();
        if (workingPos[0] < -currentNeighborhoodRegion.radius) {
            initializeHistogram(axis);
        } else {
            updateHistogram(axis);
        }
    }

    private void initializeHistogram(int axis) {
        pixelHistogram.clear();
        currentNeighborhoodRegion.traverseSphere(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r >= -distance; r--) {
                        centerCoords[d] = r;
                        CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos);
                        sourceAccess.setPosition(workingPos);
                        pixelHistogram.add(sourceAccess.get());
                    }
                }
        );
    }

    private void updateHistogram(int axis) {
        prevNeighborhoodRegion.traverseSphere(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (!removePointFromPrevNeighborhood(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos))) {
                            return;
                        }

                        centerCoords[d] = -r;
                        if (!removePointFromPrevNeighborhood(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos))) {
                            return;
                        }
                    }
                    centerCoords[d] = 0;
                    removePointFromPrevNeighborhood(CoordUtils.addCoords(prevNeighborhoodRegion.center, centerCoords, workingPos));
                }
        );
        currentNeighborhoodRegion.traverseSphere(
                axis,
                (long[] centerCoords, long distance, int d) -> {
                    for (long r = distance; r > 0; r--) {
                        centerCoords[d] = r;
                        if (!addPointFromNewNeighborhood(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos))) {
                            return;
                        }

                        centerCoords[d] = -r;
                        if (!addPointFromNewNeighborhood(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos))) {
                            return;
                        }
                    }
                    centerCoords[d] = 0;
                    addPointFromNewNeighborhood(CoordUtils.addCoords(currentNeighborhoodRegion.center, centerCoords, workingPos));
                }
        );
    }

    private boolean removePointFromPrevNeighborhood(long[] point) {
        if (currentNeighborhoodRegion.contains(point)) {
            return false;
        }
        sourceAccess.setPosition(point);
        pixelHistogram.remove(sourceAccess.get());
        return true;
    }

    private boolean addPointFromNewNeighborhood(long[] point) {
        if (prevNeighborhoodRegion.contains(point)) {
            return false;
        }
        sourceAccess.setPosition(point);
        pixelHistogram.add(sourceAccess.get());
        return true;
    }

}
