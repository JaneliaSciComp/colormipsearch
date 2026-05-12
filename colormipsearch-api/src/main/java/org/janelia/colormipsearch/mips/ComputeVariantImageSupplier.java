package org.janelia.colormipsearch.mips;

import java.io.Serializable;
import java.util.function.Supplier;

import org.janelia.colormipsearch.image.ImageArray;

/**
 * Named image provider interface
 */
public interface ComputeVariantImageSupplier extends Serializable {

    static ComputeVariantImageSupplier fromNameAndImageSupplier(String name, Supplier<ImageArray> imageSupplier) {
        return new ComputeVariantImageSupplier() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public ImageArray getImage() {
                return imageSupplier.get();
            }
        };
    }

    String getName();

    /**
     * @return image
     */
    ImageArray getImage();
}
