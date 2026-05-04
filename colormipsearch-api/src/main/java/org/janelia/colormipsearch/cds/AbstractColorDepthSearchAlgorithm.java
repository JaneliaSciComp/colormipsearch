package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.view.FlippedImageViewAdapter;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> implements ColorDepthSearchAlgorithm<S> {

    final ImageArray queryImage;
    final ImageArray mirroredQueryImage;
    final int[] queryPositions;
    final int[] mirroredQueryPositions;
    final int targetThreshold;
    final double zTolerance;

    protected AbstractColorDepthSearchAlgorithm(ImageArray queryImage, int queryThreshold,
                                                boolean mirrorQuery, int targetThreshold, double zTolerance,
                                                ImageMaskPredicate excludedRegionsPredicate) {
        this.queryImage = ImageOperations.maskRGB(
                ImageOperations.maskRegion(queryImage, excludedRegionsPredicate),
                queryThreshold
        );
        this.targetThreshold = targetThreshold;
        this.zTolerance = zTolerance;
        this.queryPositions = getMaskPosArray(queryImage);
        if (mirrorQuery) {
            this.mirroredQueryImage = ImageOperations.flipImage(queryImage, FlippedImageViewAdapter.X_AXIS);
            this.mirroredQueryPositions = getMaskPosArray(mirroredQueryImage);
        } else {
            this.mirroredQueryImage = null;
            this.mirroredQueryPositions = null;
        }
    }

    @Override
    public ImageArray getQueryImage() {
        return queryImage;
    }

    @Override
    public int getQuerySize() {
        return queryPositions.length;
    }

    /**
     * Check the image dimensions if they are the same.
     *
     * @param target
     * @return true if the target has different shape from the current query
     */
    public boolean hasDifferentShape(ImageArray target) {
        return queryImage.getWidth() != target.getWidth()
                || queryImage.getHeight() != target.getHeight()
                || queryImage.getDepth() != target.getDepth();
    }

    ImageArray getMirroredQueryImage() {
        return mirroredQueryImage;
    }

    int[] queryPixelPositions() {
        return queryPositions;
    }

    int[] mirroredQueryPixelPositions() {
        return mirroredQueryPositions;
    }

    private int[] getMaskPosArray(ImageArray msk) {
        List<Integer> pos = new ArrayList<>();
        for (int pi = 0; pi < msk.getSpatialSize(); pi++) {
            int pix = msk.getPackedIntValAtIndex(pi);
            if (pix != 0) {
                pos.add(pi);
            }
        }
        return pos.stream().mapToInt(i -> i).toArray();
    }
}
