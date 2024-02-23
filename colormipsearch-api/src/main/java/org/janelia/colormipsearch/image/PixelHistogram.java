package org.janelia.colormipsearch.image;

public interface PixelHistogram<T> {
    /**
     * Add a value and return the new max
     * @param val
     * @return the new max value
     */
    T add(T val);

    /**
     * Remove the value and return the new max
     * @param p
     * @return the new max value
     */
    T remove(T val);

    void clear();

    T maxVal();

    PixelHistogram<T> copy();
}
