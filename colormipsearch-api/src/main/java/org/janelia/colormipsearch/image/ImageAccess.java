package org.janelia.colormipsearch.image;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;

public interface ImageAccess<T> extends RandomAccessibleInterval<T>, IterableInterval<T> {
    /**
     * Pixel background value.
     * @return
     */
    T getBackgroundValue();

    /**
     * Check if the input is the same as image background.
     * @param value
     * @return
     */
    default boolean isBackgroundValue(T value) {
        return getBackgroundValue().equals(value);
    }

    /**
     * Image shape.
     * @return
     */
    default long[] getImageShape() {
        return dimensionsAsLongArray();
    }

    /**
     * Check if image shapes are equal.
     * @param img
     * @return
     */
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
