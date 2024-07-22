package org.janelia.colormipsearch.image;

public interface Max<T> {
    /**
     * Set the max of p1 and p2 to r.
     *
     * @param p1
     * @param p2
     * @param r
     * @return
     */
    void maxOf(T p1, T p2, T r);
}
