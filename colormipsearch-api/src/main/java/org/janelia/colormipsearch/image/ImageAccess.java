package org.janelia.colormipsearch.image;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;

public interface ImageAccess<T> extends RandomAccessibleInterval<T>, IterableInterval<T> {
    T getBackgroundValue();

    default boolean isBackgroundValue(T value) {
        return getBackgroundValue().equals(value);
    }

    default long[] getImageShape() {
        return dimensionsAsLongArray();
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
