package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

class AbstractGradientScoresArgs extends AbstractColorDepthMatchArgs {

    @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space: {JRC2018_Unisex_20x_HR, JRC2018_VNC_Unisex_40x_DS} ", required = true)
    String alignmentSpace;

    @Parameter(names = {"--masks-libraries", "-md"}, required = true, variableArity = true,
            converter = ListArg.ListArgConverter.class,
            description = "Masks libraries; for JSON results this is interpreted as the location of the match files")
    List<ListArg> masksLibraries;

    @Parameter(names = {"--masks-published-names"}, description = "Masks published names to be selected for gradient scoring",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> masksPublishedNames = new ArrayList<>();

    @Parameter(names = {"--masks-mips"}, description = "Selected mask MIPs",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> masksMIPIDs;

    @Parameter(names = {"--masks-datasets"}, description = "Datasets associated with the mask of the match to be scored",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> maskDatasets = new ArrayList<>();

    @Parameter(names = {"--masks-tags"}, description = "Tags associated with the mask of the match to be scored",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> maskTags = new ArrayList<>();

    @Parameter(names = {"--targets-datasets"}, description = "Datasets associated with the target of the match to be scored",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> targetDatasets = new ArrayList<>();

    @Parameter(names = {"--targets-tags"}, description = "Tags associated with the target of the match to be scored",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> targetTags = new ArrayList<>();

    @Parameter(names = {"--targets-libraries"}, description = "Target libraries for the selected matches",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> targetsLibraries;

    @Parameter(names = {"--targets-published-names"}, description = "Selected target names",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> targetsPublishedNames;

    @Parameter(names = {"--targets-mips"}, description = "Selected target MIPs",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> targetsMIPIDs;

    @Parameter(names = {"--match-tags"}, description = "Match tags to be scored",
            listConverter = ListValueAsFileArgConverter.class,
            variableArity = true)
    List<String> matchTags = new ArrayList<>();

    @Parameter(names = {"--processing-tag"}, required = true,
            description = "Associate this tag with the run. Also all MIPs that are color depth searched will be stamped with this processing tag")
    String processingTag;

    AbstractGradientScoresArgs(CommonArgs commonArgs) {
        super(commonArgs);
    }

    String getProcessingTag() {
        return processingTag.trim();
    }
}
