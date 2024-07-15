package org.janelia.colormipsearch.image;

import java.util.function.BiPredicate;

import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.IntegerType;

public class MaskedPixelAccess<T extends IntegerType<T>> extends AbstractPositionableRandomAccessWrapper<T> {

    /**
     * Predicate that defines the mask condition based on pixel's location and pixel value.
     */
    private final BiPredicate<long[], T> maskCond;
    /**
     * Value to use when the mask condition is met.
     */
    private final T background;
    private final T foreground;

    public MaskedPixelAccess(RandomAccess<T> source, BiPredicate<long[], T> maskCond, T foreground) {
        super(source, source.numDimensions());
        this.maskCond = maskCond;
        this.foreground = foreground;
        this.background = source.get().createVariable();
        this.background.setZero();
    }

    private MaskedPixelAccess(MaskedPixelAccess<T> c) {
        super(c);
        this.maskCond = c.maskCond;
        this.foreground = c.foreground != null ? c.foreground.copy() : null;
        this.background = c.background.createVariable();
    }

    @Override
    public T get() {
        T v = source.get();
        if (maskCond.test(thisAccessPos, v)) {
            // mask condition is true
            return background;
        } else {
            return foreground != null ? foreground : v;
        }
    }

    @Override
    public MaskedPixelAccess<T> copy() {
        return new MaskedPixelAccess<>(this);
    }

    void addAccessPos(final long distance, final int d) {
        super.addAccessPos(distance, d);
        source.move(distance, d);
    }

    void setAccessPos(final long location, final int d) {
        super.setAccessPos(location, d);
        source.setPosition(location, d);
    }

}
