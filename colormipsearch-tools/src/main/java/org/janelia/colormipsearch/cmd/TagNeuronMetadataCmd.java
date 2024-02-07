package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableMap;

import org.janelia.colormipsearch.dao.AppendFieldValueHandler;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TagNeuronMetadataCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(TagNeuronMetadataCmd.class);

    @Parameters(commandDescription = "Tag neuron metadata")
    static class TagNeuronMetadataArgs extends AbstractCmdArgs {
        @Parameter(names = {"-as", "--alignment-space"}, description = "Alignment space")
        String alignmentSpace;

        @Parameter(names = {"-l", "--library"}, description = "Library names from which mips or matches are selected for export",
                variableArity = true)
        List<String> libraries = new ArrayList<>();

        @Parameter(names = {"--processing-tags"}, variableArity = true,
                converter = NameValueArg.NameArgConverter.class,
                description = "Processing tags to select")
        List<NameValueArg> processingTags = new ArrayList<>();

        @Parameter(names = {"--data-labels"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true, description = "Data labels to select")
        List<String> dataLabels = new ArrayList<>();

        @Parameter(names = {"--data-tags"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true, description = "Data tags to select")
        List<String> dataTags = new ArrayList<>();

        @Parameter(names = {"--excluded-data-tags"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true, description = "If any of these tags is present do not assign the new tag")
        List<String> excludedDataTags = new ArrayList<>();

        @Parameter(names = {"--mip-ids"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "MIP IDs to tag")
        List<String> mipIds = new ArrayList<>();

        @Parameter(names = {"--source-refs"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "Sample or Body references to tag")
        List<String> sourceRefs = new ArrayList<>();

        @Parameter(names = {"--published-names"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "Published names to tag")
        List<String> publishedNames = new ArrayList<>();

        @Parameter(names = {"--tag"}, required = true, description = "Tag to assign")
        String tag;

        TagNeuronMetadataArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final TagNeuronMetadataArgs args;

    TagNeuronMetadataCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new TagNeuronMetadataArgs(commonArgs);
    }

    @Override
    TagNeuronMetadataArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        updateNeuronMetadataTags();
    }

    private void updateNeuronMetadataTags() {
        NeuronSelector neuronSelector = new NeuronSelector()
                .setAlignmentSpace(args.alignmentSpace)
                .addLibraries(args.libraries)
                .addMipIDs(args.mipIds)
                .addSourceRefIds(args.sourceRefs)
                .addNames(args.publishedNames)
                .addDatasetLabels(args.dataLabels)
                .addTags(args.dataTags)
                .addExcludedTags(args.excludedDataTags);
        args.processingTags.forEach(nv -> neuronSelector.addNewProcessedTagsSelection(nv.argName, nv.argValues));
        DaosProvider daosProvider = getDaosProvider();
        long nUpdates = daosProvider.getNeuronMetadataDao().updateAll(
                neuronSelector,
                ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton(args.tag))));
        LOG.info("Tagged {} entries with {}", nUpdates, args.tag);
    }
}
