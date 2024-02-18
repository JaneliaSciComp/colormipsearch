package org.janelia.colormipsearch.image;

import java.util.List;
import java.util.function.Consumer;

import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public class MaxFilterRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    /**
     * Stateful mechanism to update a value from another value.
     * @param <T>
     */
    public interface ValueUpdater<T> {
        /**
         * @param v
         * @return
         */
        T updateFrom(T v);
    }

    private final List<HypersphereWithHistogramNeighborhoodRandomAccess<T>> neighborhoodAccessors;
    private final ValueUpdater<T> valueInitializer;
    private final ValueUpdater<T> valueUpdater;

    MaxFilterRandomAccess(RandomAccess<T> source,
                          List<HypersphereWithHistogramNeighborhoodRandomAccess<T>> neighborhoodAccessors,
                          ValueUpdater<T> valueInitializer,
                          ValueUpdater<T> valueUpdater) {
        super(source);
        this.neighborhoodAccessors = neighborhoodAccessors;
        this.valueInitializer = valueInitializer;
        this.valueUpdater = valueUpdater;
    }

    private void updatePosition(Consumer<RandomAccess<T>> sourceAction, Consumer<RandomAccess<Neighborhood<T>>> neighborhoodAction) {
        sourceAction.accept(source);
        for (RandomAccess<Neighborhood<T>> neighborhoodRandomAccess: neighborhoodAccessors) {
            neighborhoodAction.accept(neighborhoodRandomAccess);
        }
    }

    @Override
    public void fwd(final int d) {
        updatePosition(sa -> sa.fwd(d), na -> na.fwd(d));
    }

    @Override
    public void bck(final int d) {
        updatePosition(sa -> sa.bck(d), na -> na.bck(d));
    }

    @Override
    public void move(final int distance, final int d) {
        updatePosition(sa -> sa.move(distance, d), na -> na.move(distance, d));
    }

    @Override
    public void move(final long distance, final int d) {
        updatePosition(sa -> sa.move(distance, d), na -> na.move(distance, d));
    }

    @Override
    public void move(final Localizable localizable) {
        updatePosition(sa -> sa.move(localizable), na -> na.move(localizable));
    }

    @Override
    public void move(final int[] distance) {
        updatePosition(sa -> sa.move(distance), na -> na.move(distance));
    }

    @Override
    public void move(final long[] distance) {
        updatePosition(sa -> sa.move(distance), na -> na.move(distance));
    }

    @Override
    public void setPosition(final Localizable localizable) {
        updatePosition(sa -> sa.setPosition(localizable), na -> na.setPosition(localizable));
    }

    @Override
    public void setPosition(final int[] position) {
        updatePosition(sa -> sa.setPosition(position), na -> na.setPosition(position));
    }

    @Override
    public void setPosition(final long[] position) {
        updatePosition(sa -> sa.setPosition(position), na -> na.setPosition(position));
    }

    @Override
    public void setPosition(final int position, final int d) {
        updatePosition(sa -> sa.setPosition(position, d), na -> na.setPosition(position, d));
    }

    @Override
    public void setPosition(final long position, final int d) {
        updatePosition(sa -> sa.setPosition(position, d), na -> na.setPosition(position, d));
    }

    @Override
    public T get() {
        T currentValue = valueInitializer.updateFrom(source.get());
        for (HypersphereWithHistogramNeighborhoodRandomAccess<T> neighborhoodRandomAccess: neighborhoodAccessors) {
            currentValue = valueUpdater.updateFrom(neighborhoodRandomAccess.getPixel());
            //            for (int d = 0; d < numDimensions(); d++) {
//                neighborhoodRandomAccess.setPosition(getLongPosition(d), d);
//            }
//            Neighborhood<T> neighborhood = neighborhoodRandomAccess.get();
//            Cursor<T> neighborhoodCursor = neighborhood.cursor();
//            while (neighborhoodCursor.hasNext()) {
//                currentValue = valueUpdater.updateFrom(neighborhoodCursor.next());
//            }
        }
        return currentValue;
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<T>(
                source.copy(),
                neighborhoodAccessors,
                valueInitializer,
                valueUpdater
        );
    }

}
