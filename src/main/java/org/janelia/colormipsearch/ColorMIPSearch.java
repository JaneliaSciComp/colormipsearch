package org.janelia.colormipsearch;

import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
abstract class ColorMIPSearch implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ColorMIPSearch.class);

    final String gradientMasksPath;
    private final Integer dataThreshold;
    private final Integer maskThreshold;
    private final Integer xyShift;
    private final boolean mirrorMask;
    private final Double pixColorFluctuation;
    private final Double pctPositivePixels;
    private final int negativeRadius;
    private final EM2LMAreaGapCalculator gradientBasedScoreAdjuster;

    ColorMIPSearch(String gradientMasksPath,
                   Integer dataThreshold,
                   Integer maskThreshold,
                   Double pixColorFluctuation,
                   Integer xyShift,
                   int negativeRadius,
                   boolean mirrorMask,
                   Double pctPositivePixels) {
        this.gradientMasksPath = gradientMasksPath;
        this.dataThreshold = dataThreshold;
        this.maskThreshold = maskThreshold;
        this.pixColorFluctuation = pixColorFluctuation;
        this.xyShift = xyShift;
        this.mirrorMask = mirrorMask;
        this.pctPositivePixels = pctPositivePixels;
        this.negativeRadius = negativeRadius;
        this.gradientBasedScoreAdjuster = new EM2LMAreaGapCalculator(maskThreshold, negativeRadius, mirrorMask);
    }

    Map<String, String> getCDSParameters() {
        Map<String, String> cdsParams = new LinkedHashMap<>();
        cdsParams.put("gradientMasksPath", gradientMasksPath);
        cdsParams.put("dataThreshold", dataThreshold != null ? dataThreshold.toString() : null);
        cdsParams.put("maskThreshold", maskThreshold != null ? maskThreshold.toString() : null);
        cdsParams.put("xyShift", xyShift != null ? xyShift.toString() : null);
        cdsParams.put("mirrorMask", String.valueOf(mirrorMask));
        cdsParams.put("pixColorFluctuation", pixColorFluctuation != null ? pixColorFluctuation.toString() : null);
        cdsParams.put("pctPositivePixels", pctPositivePixels != null ? pctPositivePixels.toString() : null);
        cdsParams.put("negativeRadius", String.valueOf(negativeRadius));
        return cdsParams;
    }

    abstract List<ColorMIPSearchResult> findAllColorDepthMatches(List<MIPInfo> maskMIPS, List<MIPInfo> libraryMIPS);

    ColorMIPSearchResult runImageComparison(MIPImage libraryMIPImage, MIPImage maskMIPImage) {
        long startTime = System.currentTimeMillis();
        try {
            LOG.debug("Compare library file {} with mask {}", libraryMIPImage,  maskMIPImage);
            double pixfludub = pixColorFluctuation / 100;

            final ColorMIPMaskCompare cc = new ColorMIPMaskCompare(
                    maskMIPImage.imageArray,
                    maskThreshold,
                    mirrorMask,
                    null,
                    0,
                    mirrorMask,
                    dataThreshold,
                    pixfludub,
                    xyShift
            );
            ColorMIPMaskCompare.Output output = cc.runSearch(libraryMIPImage.imageArray);

            double pixThresdub = pctPositivePixels / 100;
            boolean isMatch = output.matchingPct > pixThresdub;

            return new ColorMIPSearchResult(maskMIPImage.mipInfo, libraryMIPImage.mipInfo, output.matchingPixNum, output.matchingPct, isMatch, false);
        } catch (Throwable e) {
            LOG.warn("Error comparing library file {} with mask {}", libraryMIPImage,  maskMIPImage, e);
            return new ColorMIPSearchResult(maskMIPImage.mipInfo, libraryMIPImage.mipInfo, 0, 0, false, true);
        } finally {
            LOG.debug("Completed comparing library file {} with mask {} in {}ms", libraryMIPImage,  maskMIPImage, System.currentTimeMillis() - startTime);
        }
    }

    ColorMIPSearchResult applyGradientAreaAdjustment(ColorMIPSearchResult sr, MIPImage libraryMIPImage, MIPImage libraryGradientImage, MIPImage patternMIPImage, MIPImage patternGradientImage) {
        if (sr.isMatch()) {
            return sr.applyGradientAreaGap(calculateGradientAreaAdjustment(libraryMIPImage, libraryGradientImage, patternMIPImage, patternGradientImage));
        } else {
            return sr;
        }
    }

    ColorMIPSearchResult.AreaGap calculateGradientAreaAdjustment(MIPImage libraryMIPImage, MIPImage libraryGradientImage, MIPImage patternMIPImage, MIPImage patternGradientImage) {
        ColorMIPSearchResult.AreaGap areaGap;
        long startTime = System.currentTimeMillis();
        if (patternMIPImage.mipInfo.isEmSkelotonMIP() || libraryGradientImage != null) {
            areaGap = gradientBasedScoreAdjuster.calculateAdjustedScore(libraryMIPImage, patternMIPImage, libraryGradientImage);
            LOG.debug("Completed calculating area gap between {} and {} with {} in {}ms", libraryMIPImage, patternMIPImage, libraryGradientImage, System.currentTimeMillis() - startTime);
        } else if (patternGradientImage != null) {
            areaGap = gradientBasedScoreAdjuster.calculateAdjustedScore(patternMIPImage, libraryMIPImage, patternGradientImage);
            LOG.debug("Completed calculating area gap between {} and {} with {} in {}ms", libraryMIPImage, patternMIPImage, patternGradientImage, System.currentTimeMillis() - startTime);
        } else {
            areaGap = null;
        }
        return areaGap;
    }

    Comparator<ColorMIPSearchResult> getColorMIPSearchComparator() {
        return Comparator.comparingInt(ColorMIPSearchResult::getMatchingPixels).reversed();
    }

    void terminate() {
    }
}
