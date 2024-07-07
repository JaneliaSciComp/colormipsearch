package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.imglib2.stream.Streams;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.GeomTransform;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.MirrorTransform;
import org.janelia.colormipsearch.image.ShiftTransform;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * PixelMatchColorDepthQuerySearchAlgorithm - implements the color depth mip comparison
 * using internal arrays containg the positions from the mask that are above the mask threshold
 * and the positions after applying the specified x-y shift and mirroring transformations.
 * The mask pixels are compared against the target pixels tht
 */
public class PixelMatchColorDepthSearchAlgorithm<P extends RGBPixelType<P>, G extends IntegerType<G>> extends AbstractColorDepthSearchAlgorithm<PixelMatchScore, P, G> {

    private final GeomTransform[] shiftTransforms;
    private final boolean includeMirroredTargets;

    public PixelMatchColorDepthSearchAlgorithm(ImageAccess<P> queryImage,
                                               int queryThreshold,
                                               int targetThreshold,
                                               boolean includeMirroredTargets,
                                               double zTolerance, int shiftValue) {
        super(queryImage, queryThreshold, targetThreshold, zTolerance);
        this.includeMirroredTargets = includeMirroredTargets;
        // shifting
        shiftTransforms = generateShiftTransforms(shiftValue);
    }

    private GeomTransform[] generateShiftTransforms(int shiftValue) {
        // the image is shifted in increments of 2 pixels, i.e. 2, 4, 6, ...
        // so the shiftValue must be an even number
        if ((shiftValue & 0x1) == 0x1) {
            throw new IllegalArgumentException("Invalid shift value: " + shiftValue +
                    " - the targets is shifted in increments of 2 pixels because 1 pixel shift is too small");
        }
        int ndims = getQueryImage().numDimensions();
        return ImageAccessUtils.streamNeighborsWithinDist(ndims, shiftValue/2, true)
                .map(c -> CoordUtils.mulCoords(c, 2))
                .map(ShiftTransform::new)
                .toArray(GeomTransform[]::new);
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetRGBVariantTypes() {
        return Collections.emptySet();
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetGrayVariantTypes() {
        return Collections.emptySet();
    }

    @Override
    public PixelMatchScore calculateMatchingScore(@Nonnull ImageAccess<P> targetImage,
                                                  Map<ComputeFileType, Supplier<ImageAccess<P>>> rgbVariantsSuppliers,
                                                  Map<ComputeFileType, Supplier<ImageAccess<G>>> targetGrayVariantsSuppliers) {
        long querySize = getQuerySize();
        if (querySize == 0) {
            return new PixelMatchScore(0, 0, false);
        } else if (getQueryImage().hasDifferentShape(targetImage)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid image size - target's image shape %s must match query's image: %s",
                    Arrays.toString(targetImage.getImageShape()), Arrays.toString(getQueryImage().getImageShape())));
        }
        boolean bestScoreMirrored = false;
        int matchingPixelsScore = Arrays.stream(shiftTransforms)
                .map(transform -> ImageTransforms.createGeomTransformation(targetImage, transform))
                .mapToInt(targetPixelAccess -> countColorDepthMatches(getQueryImage(), targetPixelAccess))
                .max()
                .orElse(0);
        if (includeMirroredTargets) {
            GeomTransform mirrorTransform = new MirrorTransform(
                    getQueryImage().minAsLongArray(),
                    getQueryImage().maxAsLongArray(),
                    0);
            int mirroredMatchingScore = Arrays.stream(shiftTransforms)
                    .map(geomTransform -> geomTransform.compose(mirrorTransform))
                    .map(transform -> ImageTransforms.createGeomTransformation(targetImage, transform))
                    .mapToInt(targetPixelAccess -> countColorDepthMatches(getQueryImage(), targetPixelAccess))
                    .max()
                    .orElse(0);
            if (mirroredMatchingScore > matchingPixelsScore) {
                matchingPixelsScore = mirroredMatchingScore;
                bestScoreMirrored = true;
            }
        }
        double matchingPixelsRatio = (double)matchingPixelsScore / (double)querySize;
        return new PixelMatchScore(matchingPixelsScore, matchingPixelsRatio, bestScoreMirrored);
    }

    private int countColorDepthMatches(ImageAccess<? extends RGBPixelType<?>> queryImage,
                                       ImageAccess<? extends RGBPixelType<?>> targetImage) {
        CDPixelMatchOp pixelMatchOp = new CDPixelMatchOp();
        return Streams.localizable(queryImage)
                .map(localizableSampler -> {
                    long[] pos = localizableSampler.positionAsLongArray();
                    RGBPixelType<?> qp = queryImage.randomAccess().setPositionAndGet(pos);
                    RGBPixelType<?> tp = targetImage.randomAccess().setPositionAndGet(pos);
                    int tred = tp.getRed();
                    int tgreen = tp.getGreen();
                    int tblue = tp.getBlue();

                    if (tred > targetThreshold || tgreen > targetThreshold || tblue > targetThreshold) {
                        int qred = qp.getRed();
                        int qgreen = qp.getGreen();
                        int qblue = qp.getBlue();
                        double pxGap = pixelMatchOp.calculatePixelGapFromRGBValues(qred, qgreen, qblue, tred, tgreen, tblue);
                        return pxGap <= zTolerance ? 1 : 0;
                    } else {
                        return 0;
                    }
                })
                .reduce(0, (s1, s2) -> s1 + s2);
    }

}
