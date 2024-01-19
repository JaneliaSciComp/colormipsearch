package org.janelia.colormipsearch.image;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;

public interface ImageAccess<T> {
    Interval getInterval();
    RandomAccess<T> getRandomAccess();
    Cursor<T> getCursor();
    T getBackgroundValue();
    long getSize();

    default boolean isBackgroundValue(T value) {
        return getBackgroundValue().equals(value);
    }

    default long[] getImageShape() {
        return getInterval().dimensionsAsLongArray();
    }

    default int getNumDimensions() {
        return getInterval().numDimensions();
    }

    default boolean hasSameShape(ImageAccess<?> img) {
        long[] thisShape = this.getImageShape();
        long[] imgShape = img.getImageShape();
        if (thisShape.length != imgShape.length)
            return false;
        for (int d = 0; d < thisShape.length; d++) {
            if (thisShape[d] != imgShape[d])
                return false;
        }
        return true;
    }

    default boolean hasDifferentShape(ImageAccess<?> img) {
        return !hasSameShape(img);
    }
}
