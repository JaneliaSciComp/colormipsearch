package org.janelia.colormipsearch.api.cdsearch;

import java.util.function.BiPredicate;

import org.janelia.colormipsearch.api.imageprocessing.ImageArray;

public interface ImageRegionGenerator {
    /**
     * Create the region predicate which should return true if the pixel at x, y is true.
     *
     * @param imageArray
     * @return
     */
    BiPredicate<Integer, Integer> getRegion(ImageArray<?> imageArray);
}
