package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Creates a color depth search for a given mask.
 */
public class ColorMIPSearch<P extends RGBPixelType<P>, G extends IntegerType<G>> implements Serializable {

    private final ColorDepthSearchAlgorithmProvider<PixelMatchScore, P, G> cdsAlgorithmProvider;
    private final Integer defaultQueryThreshold;
    private final Double pctPositivePixels;
    private final P rgbPixel;
    private final G grayPixel;

    public ColorMIPSearch(Double pctPositivePixels,
                          Integer defaultQueryThreshold,
                          ColorDepthSearchAlgorithmProvider<PixelMatchScore, P, G> cdsAlgorithmProvider,
                          P rgbPixel,
                          G grayPixel) {
        this.pctPositivePixels = pctPositivePixels;
        this.defaultQueryThreshold = defaultQueryThreshold;
        this.cdsAlgorithmProvider = cdsAlgorithmProvider;
        this.rgbPixel = rgbPixel;
        this.grayPixel = grayPixel;
    }

    public Map<String, Object> getCDSParameters() {
        Map<String, Object> cdsParams = new LinkedHashMap<>(cdsAlgorithmProvider.getDefaultCDSParams().asMap());
        cdsParams.put("pctPositivePixels", pctPositivePixels != null ? pctPositivePixels.toString() : null);
        cdsParams.put("defaultMaskThreshold", defaultQueryThreshold != null ? defaultQueryThreshold.toString() : null);
        return cdsParams;
    }

    public ColorDepthSearchAlgorithm<PixelMatchScore, P, G> createQueryColorDepthSearchWithDefaultThreshold(RandomAccessibleInterval<P> queryImage) {
        return cdsAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(queryImage, defaultQueryThreshold == null ? 0 : defaultQueryThreshold);
    }

    public ColorDepthSearchAlgorithm<PixelMatchScore, P, G> createQueryColorDepthSearch(RandomAccessibleInterval<P> queryImage, int queryThreshold) {
        return cdsAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(queryImage, queryThreshold);
    }

    public boolean isMatch(PixelMatchScore pixelMatchScore) {
        double pixMatchRatioThreshold = pctPositivePixels != null ? pctPositivePixels / 100 : 0.;
        return pixelMatchScore.getScore() > 0 && pixelMatchScore.getNormalizedScore() > pixMatchRatioThreshold;
    }

    public P getRgbPixel() {
        return rgbPixel;
    }

    public G getGrayPixel() {
        return grayPixel;
    }
}
