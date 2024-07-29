package org.janelia.colormipsearch.cds;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * Creates a color depth search for a given mask.
 */
public class ColorMIPSearch implements Serializable {

    private final ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider;
    private final Integer defaultQueryThreshold;
    private final Double pctPositivePixels;

    public ColorMIPSearch(Double pctPositivePixels,
                          Integer defaultQueryThreshold,
                          ColorDepthSearchAlgorithmProvider<PixelMatchScore> cdsAlgorithmProvider) {
        this.pctPositivePixels = pctPositivePixels;
        this.defaultQueryThreshold = defaultQueryThreshold;
        this.cdsAlgorithmProvider = cdsAlgorithmProvider;
    }

    public Map<String, Object> getCDSParameters() {
        Map<String, Object> cdsParams = new LinkedHashMap<>(cdsAlgorithmProvider.getDefaultCDSParams().asMap());
        cdsParams.put("pctPositivePixels", pctPositivePixels != null ? pctPositivePixels.toString() : null);
        cdsParams.put("defaultMaskThreshold", defaultQueryThreshold != null ? defaultQueryThreshold.toString() : null);
        return cdsParams;
    }

    public ColorDepthSearchAlgorithm<PixelMatchScore> createQueryColorDepthSearchWithDefaultThreshold(
            RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
            Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers
    ) {
        return cdsAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                queryImage,
                queryVariantsSuppliers,
                defaultQueryThreshold == null ? 0 : defaultQueryThreshold
        );
    }

    public ColorDepthSearchAlgorithm<PixelMatchScore> createQueryColorDepthSearch(
            RandomAccessibleInterval<? extends RGBPixelType<?>> queryImage,
            Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> queryVariantsSuppliers,
            int queryThreshold) {
        return cdsAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                queryImage, queryVariantsSuppliers, queryThreshold
        );
    }

    public boolean isMatch(PixelMatchScore pixelMatchScore) {
        double pixMatchRatioThreshold = pctPositivePixels != null ? pctPositivePixels / 100 : 0.;
        return pixelMatchScore.getScore() > 0 && pixelMatchScore.getNormalizedScore() > pixMatchRatioThreshold;
    }
}
