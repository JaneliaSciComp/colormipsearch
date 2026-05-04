package org.janelia.colormipsearch.image;

/**
 * 3-dimensional mutable image array representation with support for updating image content.
 */
public interface WriteableImageArray extends ImageArray {

    void setPackedIntValAtIndex(int pi, int val);

    void setChannelIntValAtIndex(int pi, int ch, int val);

    default void setPackedFloatValAtIndex(int pi, float val) {
        setPackedIntValAtIndex(pi, (int) val);
    }

    default void setChannelFloatValAtIndex(int pi, int ch, float val) {
        setChannelIntValAtIndex(pi, ch, (int) val);
    }

    default void setPackedIntValAtCoords(int x, int y, int z, int val) {
        setPackedIntValAtIndex(getSpatialLinearIndex(x, y, z), val);
    }

    default void setChannelIntValAtCoords(int x, int y, int z, int ch, int val) {
        setChannelIntValAtIndex(getSpatialLinearIndex(x, y, z), ch, val);
    }

    default void setPackedFloatValAtCoords(int x, int y, int z, float val) {
        setPackedFloatValAtIndex(getSpatialLinearIndex(x, y, z), val);
    }

    default void setChannelFloatValAtCoords(int x, int y, int z, int ch, float val) {
        setChannelFloatValAtIndex(getSpatialLinearIndex(x, y, z), ch, val);
    }
}
