package org.janelia.colormipsearch.image;

import net.imglib2.Interval;

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

    Interval histogramInterval();

    void updateHistogramInterval(Interval interval);

    T maxVal();

    PixelHistogram<T> copy();
}
