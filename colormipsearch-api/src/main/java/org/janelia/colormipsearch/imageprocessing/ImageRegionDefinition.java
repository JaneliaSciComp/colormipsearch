package org.janelia.colormipsearch.imageprocessing;

import java.util.function.BiPredicate;

public interface ImageRegionDefinition {
    /**
     * Create the region predicate which should return true if the pixel at x, y is true.
     *
     * @param imageArray
     * @return
     */
    BiPredicate<Integer, Integer> getRegion(ImageArray<?> imageArray);
}
