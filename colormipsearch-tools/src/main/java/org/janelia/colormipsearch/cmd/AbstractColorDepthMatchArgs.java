package org.janelia.colormipsearch.cmd;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import com.beust.jcommander.Parameter;

import org.apache.commons.lang3.StringUtils;

class AbstractColorDepthMatchArgs extends AbstractCmdArgs {
    @Parameter(names = "--app")
    String appName = "ColorMIPSearch";

    @Parameter(names = {"--dataThreshold"}, description = "Data threshold")
    Integer dataThreshold = 100;

    @Parameter(names = {"--maskThreshold"}, description = "Mask threshold")
    Integer maskThreshold = 100;

    @Parameter(names = {"--pixColorFluctuation"}, description = "Pix Color Fluctuation, 1.18 per slice")
    Double pixColorFluctuation = 2.0;

    @Parameter(names = {"--xyShift"}, description = "Number of pixels to try shifting in XY plane. This must be an even natural number - typically: 0, 2, or 4")
    Integer xyShift = 0;

    @Parameter(names = {"--negativeRadius"}, description = "Radius for gradient based score adjustment (negative radius)")
    int negativeRadius = 20;

    @Parameter(names = {"--mirrorMask"}, description = "Should the mask be mirrored across the Y axis?", arity = 0)
    boolean mirrorMask = false;

    @Parameter(names = {"--pctPositivePixels"}, description = "% of Positive PX Threshold (0-100%)")
    Double pctPositivePixels = 0.0;

    @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
    int processingPartitionSize = 100;

    @Parameter(names = {"--no-name-labels"},
            description = "If true the mips do not have the name labels so they do not need to be cleared",
            arity = 0)
    boolean noNameLabel = false;

    @Parameter(names = {"--no-colormap-labels"},
            description = "If true the mips do not have the color map labels so they do not need to be cleared",
            arity = 0)
    boolean noColorScaleLabel = false;

    @Parameter(names = {"--libraryFilter", "-lf"}, variableArity = true, description = "Filter for library mips")
    Set<String> libraryMIPsFilter;

    @Parameter(names = {"--masksFilter", "-mf"}, variableArity = true, description = "Filter for mask mips")
    Set<String> maskMIPsFilter;

    @Parameter(names = {"--perMaskSubdir"}, description = "Results subdirectory for results grouped by mask MIP ID")
    String perMaskSubdir;

    @Parameter(names = {"--perTargetSubdir"}, description = "Results subdirectory for results grouped by target MIP ID")
    String perTargetSubdir;

    @Parameter(names = {"--query-roi-mask"}, description = "Global ROI mask applied to all query images. " +
            "For example this could be the hemibrain mask when searching against hemibrain libraries.")
    String queryROIMaskName;

    AbstractColorDepthMatchArgs(CommonArgs commonArgs) {
        super(commonArgs);
    }

    @Nullable
    Path getPerMaskDir() {
        return getOutputDirArg()
                .map(dir -> StringUtils.isNotBlank(perMaskSubdir) ? dir.resolve(perMaskSubdir) : dir)
                .orElse(null);
    }

    @Nullable
    Path getPerTargetDir() {
        return getOutputDirArg()
                .map(dir -> StringUtils.isNotBlank(perTargetSubdir) ? dir.resolve(perTargetSubdir) : dir)
                .orElse(null);
    }

    boolean hasNameLabel() {
        return !noNameLabel;
    }

    boolean hasColorScaleLabel() {
        return !noColorScaleLabel;
    }

    BiPredicate<long[]/*pos*/, long[]/*shape*/> getColorScaleAndLabelRegionCondition() {
        // define the text label and color scale regions
        return (long[] pos, long[] shape) -> {
            if (pos.length != shape.length) {
                throw new IllegalArgumentException("Image coordinates and dimensions must be equal");
            }
            if (pos.length != 2) {
                throw new IllegalArgumentException("Image must be a 2D-image");
            }
            long imgWidth = shape[0];
            long x = pos[0];
            long y = pos[1];
            boolean isInsideColorScale = hasColorScaleLabel() && imgWidth > 270 && x >= imgWidth - 270 && y < 90;
            boolean isInsideNameLabel = hasNameLabel() && x < 330 && y < 100;
            return isInsideColorScale || isInsideNameLabel;
        };
    }

}
