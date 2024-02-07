package org.janelia.colormipsearch.image;

import java.util.Collection;

import net.imglib2.Interval;
import net.imglib2.Point;

public interface PixelLocations<T> {
    /**
     * Add a pixel with its location
     * @param location
     * @param val
     * @return update pixel value
     */
    T add(Point location, T val);

    /**
     * Remove the specified locations
     * @param locations
     */
    T remove(Collection<Point> locations);

    /**
     * List pixel locations
     * @return a collection of stored pixel locations.
     */
    Collection<Point> list();

    Interval getInterval();

    Interval updateInterval(Interval interval);
}
