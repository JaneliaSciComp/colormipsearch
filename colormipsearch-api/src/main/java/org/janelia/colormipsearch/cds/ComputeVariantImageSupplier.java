package org.janelia.colormipsearch.cds;

import java.io.Serializable;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;

/**
 * Named image provider interface
 */
public interface ComputeVariantImageSupplier<P extends IntegerType<P>> extends Serializable {

    String getName();

    /**
     * @return image
     */
    RandomAccessibleInterval<P> getImage();
}
