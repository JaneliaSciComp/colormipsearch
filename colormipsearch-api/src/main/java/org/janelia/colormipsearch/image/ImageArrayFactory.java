package org.janelia.colormipsearch.image;

/**
 * Factory for creating ImageArray objects.
 */
@FunctionalInterface
public interface ImageArrayFactory {
    ImageArray create(int width, int height, int depth, int channels);
}
