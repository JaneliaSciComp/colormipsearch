package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.Collections;

import org.janelia.colormipsearch.image.ImageArray;

/**
 * Creates a color depth search for a given mask.
 */
public class ColorMIPSearch implements Serializable {

    private final ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider;
    private final Double pctPositivePixels;

    public ColorMIPSearch(Double pctPositivePixels,
                          ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider) {
        this.pctPositivePixels = pctPositivePixels;
        this.cdsAlgorithmProvider = cdsAlgorithmProvider;
    }

    public ColorDepthSearchAlgorithm<PixelMatchScore> createQueryColorDepthSearchWithDefaultThreshold(ImageArray queryImage) {
        return cdsAlgorithmProvider.createColorDepthSearchAlgorithm(queryImage, Collections.emptyMap());
    }


    public boolean isMatch(PixelMatchScore pixelMatchScore) {
        double pixMatchRatioThreshold = pctPositivePixels != null ? pctPositivePixels / 100 : 0.;
        return pixelMatchScore.getScore() > 0 && pixelMatchScore.getNormalizedScore() > pixMatchRatioThreshold;
    }

}
