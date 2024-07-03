package org.janelia.colormipsearch.image;

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

    @Override
    public void fwd(final int d) {
        neighborhoodsAccess.fwd(d);
    }

    @Override
    public void bck(final int d) {
        neighborhoodsAccess.bck(d);
    }

    @Override
    public void move(final int distance, final int d) {
        neighborhoodsAccess.move(distance, d);
    }

    @Override
    public void move(final long distance, final int d) {
        neighborhoodsAccess.move(distance, d);
    }

    @Override
    public void move(final Localizable localizable) {
        neighborhoodsAccess.move(localizable);
    }

    @Override
    public void move(final int[] distance) {
        neighborhoodsAccess.move(distance);
    }

    @Override
    public void move(final long[] distance) {
        neighborhoodsAccess.move(distance);
    }

    @Override
    public void setPosition(final Localizable localizable) {
        neighborhoodsAccess.setPosition(localizable);
    }

    @Override
    public void setPosition(final int[] position) {
        neighborhoodsAccess.setPosition(position);
    }

    @Override
    public void setPosition(final long[] position) {
        neighborhoodsAccess.setPosition(position);
    }

    @Override
    public void setPosition(final int position, final int d) {
        neighborhoodsAccess.setPosition(position, d);
    }

    @Override
    public void setPosition(final long position, final int d) {
        neighborhoodsAccess.setPosition(position, d);
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
