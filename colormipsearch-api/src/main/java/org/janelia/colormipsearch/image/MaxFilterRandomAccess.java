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
        source.fwd(d);
        neighborhoodsAccess.fwd(d);
    }

    @Override
    public void bck(final int d) {
        source.bck(d);
        neighborhoodsAccess.bck(d);
    }

    @Override
    public void move(final int distance, final int d) {
        source.move(distance, d);
        neighborhoodsAccess.move(distance, d);
    }

    @Override
    public void move(final long distance, final int d) {
        source.move(distance, d);
        neighborhoodsAccess.move(distance, d);
    }

    @Override
    public void move(final Localizable localizable) {
        source.move(localizable);
        neighborhoodsAccess.move(localizable);
    }

    @Override
    public void move(final int[] distance) {
        source.move(distance);
        neighborhoodsAccess.move(distance);
    }

    @Override
    public void move(final long[] distance) {
        source.move(distance);
        neighborhoodsAccess.move(distance);
    }

    @Override
    public void setPosition(final Localizable localizable) {
        source.setPosition(localizable);
        neighborhoodsAccess.setPosition(localizable);
    }

    @Override
    public void setPosition(final int[] position) {
        source.setPosition(position);
        neighborhoodsAccess.setPosition(position);
    }

    @Override
    public void setPosition(final long[] position) {
        source.setPosition(position);
        neighborhoodsAccess.setPosition(position);
    }

    @Override
    public void setPosition(final int position, final int d) {
        source.setPosition(position, d);
        neighborhoodsAccess.setPosition(position, d);
    }

    @Override
    public void setPosition(final long position, final int d) {
        source.setPosition(position, d);
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
