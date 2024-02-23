package org.janelia.colormipsearch.image;

import java.util.function.Consumer;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;

public class MaxFilterRandomAccess<T> extends AbstractRandomAccessWrapper<T> {

    private final RandomAccess<Neighborhood<T>> neighborhoodsAccess;
    private final PixelHistogram<T> slidingNeighborhoodHistogram;

    MaxFilterRandomAccess(RandomAccess<T> source,
                          RandomAccess<Neighborhood<T>> neighborhoodsAccess,
                          PixelHistogram<T> slidingNeighborhoodHistogram) {
        super(source);
        this.neighborhoodsAccess = neighborhoodsAccess;
        this.slidingNeighborhoodHistogram = slidingNeighborhoodHistogram;
    }

    private MaxFilterRandomAccess(MaxFilterRandomAccess<T> c) {
        super(c.source.copy());
        this.neighborhoodsAccess = c.neighborhoodsAccess.copy();
        this.slidingNeighborhoodHistogram = c.slidingNeighborhoodHistogram.copy();
    }

    private void updatePosition(Consumer<RandomAccess<T>> sourceAction, Consumer<RandomAccess<Neighborhood<T>>> neighborhoodAction) {
        sourceAction.accept(source);
        neighborhoodAction.accept(neighborhoodsAccess);
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
        return slidingNeighborhoodHistogram.maxVal();
    }

    @Override
    public MaxFilterRandomAccess<T> copy() {
        return new MaxFilterRandomAccess<T>(this);
    }

}
