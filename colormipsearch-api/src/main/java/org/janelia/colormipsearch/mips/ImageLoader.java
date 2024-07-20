package org.janelia.colormipsearch.mips;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import org.janelia.colormipsearch.model.FileData;

/**
 * This is used for loading the images.
 *
 * @param <P>
 */
public interface ImageLoader<P extends Type<P>> {
    int[] getExpectedSize();
    double[] getVoxelSpacing();
    RandomAccessibleInterval<P> loadImage(FileData fd);
}
