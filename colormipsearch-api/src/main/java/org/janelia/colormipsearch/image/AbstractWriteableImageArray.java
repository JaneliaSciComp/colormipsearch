package org.janelia.colormipsearch.image;

/**
 * Base class for writeable image array implementations.
 *
 * @param <T> pixel data array type (byte[], short[], float[], etc.)
 */
public abstract class AbstractWriteableImageArray<T> extends AbstractImageArray implements WriteableImageArray {

    final T pixelData;
    final int[] channelOffsets;

    protected AbstractWriteableImageArray(int width, int height, int depth, int channels) {
        super(width, height, depth, channels);
        this.pixelData = allocatePixelData();
        this.channelOffsets = new int[channels];
        for (int c = 0; c < channels; c++) {
            channelOffsets[c] = c * getSpatialSize();
        }
    }

    protected abstract T allocatePixelData();
}
