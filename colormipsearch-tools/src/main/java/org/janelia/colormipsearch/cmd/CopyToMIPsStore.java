package org.janelia.colormipsearch.cmd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.dataio.CDMIPsReader;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.janelia.colormipsearch.dataio.fs.JSONCDMIPsReader;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to calculate the gradient scores.
 */
class CopyToMIPsStore extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CopyToMIPsStore.class);
    private static final Map<String, ComputeFileType> VARIANT_FILE_TYPE_MAPPING = ImmutableMap.of (
            "cdm", ComputeFileType.SourceColorDepthImage,
            "searchable_neuron", ComputeFileType.InputColorDepthImage,
            "segmentation", ComputeFileType.InputColorDepthImage,
            "grad", ComputeFileType.GradientImage,
            "zgap", ComputeFileType.ZGapImage
    );
    private static final Pattern FILENAME_EXT_PATTERN = Pattern.compile(".+(\\..*)$");
    private static final Pattern SEGMENT_INDEX_PATTERN = Pattern.compile(".+_ch?\\d+_+(\\d+)\\..*$", Pattern.CASE_INSENSITIVE);

    @Parameters(commandDescription = "Create JACS variant library")
    static class CopyToMIPSStoreArgs extends AbstractCmdArgs {

        @Parameter(names = {"--input", "-i"}, required = true, converter = ListArg.ListArgConverter.class,
                   description = "JSON input MIPs")
        ListArg inputMIPsLibrary;

        @Parameter(names = {"--mipsFilter"}, variableArity = true, description = "Filter for i input mips")
        Set<String> mipsFilter;

        @Parameter(names = {"--targetDirectory"}, description = "Input image file(s) start index")
        String targetFolder;

        @Parameter(names = {"-n"}, description = "Only show what the command is supposed to do")
        boolean simulateFlag;

        @Parameter(names = {"-f", "--force"}, description = "Force copying the mip to the destination")
        boolean forceFlag;

        @Parameter(names = {"--lmIgnoreMissingSegmentation"}, description = "If set do not error for LM variants that do not have segment index")
        boolean ignoreMissedSegmentation = false;

        // surjective variants may have more than one image (segmentation, or flipped) associated with the original MIP
        // there may also be cases when the variant library must be one-to-one correspondence - injectiveVariants
        // but the injectiveVariants are left out until needed, they were present in commit: 2c2a80cc
        @DynamicParameter(names = {"--surjective-variants-mapping"},
                description = "Destination mapping for surjective variants - variants that may have one or more corresponding mip images")
        Map<String, String> surjectiveVariantMapping = new HashMap<>();

        CopyToMIPSStoreArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        Path getOutputDir() {
            if (StringUtils.isNotBlank(targetFolder)) {
                return Paths.get(targetFolder);
            } else if (StringUtils.isNotBlank(commonArgs.outputDir)) {
                return Paths.get(commonArgs.outputDir);
            } else {
                return null;
            }
        }
    }

    private final CopyToMIPSStoreArgs args;
    private final ObjectMapper mapper;

    CopyToMIPsStore(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new CopyToMIPSStoreArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    CopyToMIPSStoreArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        BiConsumer<FileData, Path> copyFileDataAction;
        if (args.simulateFlag) {
            copyFileDataAction = this::simulateCopyFileData;
        } else {
            copyFileDataAction = this::copyFileData;
        }
        copyMIPs(copyFileDataAction, args.getOutputDir());
    }

    private void copyMIPs(BiConsumer<FileData, Path> copyMIPVariantAction, Path targetDir) {
        CDMIPsReader cdmipsReader = getCDMipsReader();
        List<? extends AbstractNeuronEntity> mips = readMIPs(cdmipsReader);

        mips.stream()
                .filter(neuronEntity -> neuronEntity.hasComputeFile(ComputeFileType.SourceColorDepthImage))
                .parallel()
                .flatMap(neuronEntity -> {
                    String sourceCDMName = neuronEntity.getComputeFileData(ComputeFileType.SourceColorDepthImage).getNameCompOnly();
                    return args.surjectiveVariantMapping.entrySet().stream()
                            .map(variantEntry -> {
                                String variantType = variantEntry.getKey();
                                ComputeFileType ft = VARIANT_FILE_TYPE_MAPPING.get(variantType);
                                return ImmutablePair.of(ft, variantEntry.getValue());
                            })
                            .filter(fileTypeAndTargetFolder -> neuronEntity.hasComputeFile(fileTypeAndTargetFolder.getLeft()))
                            .map(fileTypeAndTargetFolder -> {
                                FileData fd = neuronEntity.getComputeFileData(fileTypeAndTargetFolder.getLeft());
                                return ImmutablePair.of(
                                        fd,
                                        targetDir.resolve(fileTypeAndTargetFolder.getRight())
                                                .resolve(createMIPVariantName(neuronEntity, sourceCDMName, fd)));
                            });
                })
                .distinct()
                .forEach(fdAndTarget -> copyMIPVariantAction.accept(fdAndTarget.getLeft(), fdAndTarget.getRight()));
    }

    private CDMIPsReader getCDMipsReader() {
        return new JSONCDMIPsReader(mapper);
    }

    private List<? extends AbstractNeuronEntity> readMIPs(CDMIPsReader mipsReader) {
        return mipsReader.readMIPs(new DataSourceParam()
                .addLibrary(args.inputMIPsLibrary.input)
                .setOffset(args.inputMIPsLibrary.offset)
                .setSize(args.inputMIPsLibrary.length)).stream()
                .filter(neuronMetadata -> CollectionUtils.isEmpty(args.mipsFilter) ||
                        args.mipsFilter.contains(neuronMetadata.getPublishedName().toLowerCase()) ||
                        args.mipsFilter.contains(neuronMetadata.getMipId()))
                .collect(Collectors.toList());
    }

    private String createMIPVariantName(AbstractNeuronEntity neuronEntity, String cdmName, FileData fd) {
        if (MIPsHandlingUtils.isEmLibrary(neuronEntity.getLibraryName())) {
            return fd.getNameCompOnly();
        } else {
            LMNeuronEntity lmNeuronEntity = (LMNeuronEntity) neuronEntity;
            return createLMMIPName(lmNeuronEntity, cdmName, fd.getNameCompOnly());
        }
    }

    private String createLMMIPName(LMNeuronEntity lmNeuronEntity, String cdmName, String variantName) {
        String baseCDMName = RegExUtils.replacePattern(cdmName, "(_CDM)?\\..*$", "");
        String internalLineName = lmNeuronEntity.getInternalLineName();
        String alignmentSpace = lmNeuronEntity.getAlignmentSpace();
        String slideCode = lmNeuronEntity.getSlideCode();
        String objective = lmNeuronEntity.getObjective();
        String anatomicalArea = lmNeuronEntity.getAnatomicalArea();
        String sampleRef = StringUtils.removeStartIgnoreCase(lmNeuronEntity.getSourceRefId(), "Sample#");
        String prefix;
        int slideCodeIndex = baseCDMName.indexOf(slideCode);
        // the logic to build the name is more complicated because using '-' as a separator does not always work
        // I found a case where the prefix actually contains hyphen in it:
        // GMR_SS02232-IVS-myr-FLAG_Syt-HA_3_0055-A01-20141118_31_G3-20x-Brain-JRC2018_Unisex_20x_HR-2087123930354548834-CH1_CDM.png
        if (slideCodeIndex == -1) {
            LOG.error("CDM name: {} does not contain the slide code ({}) in {} and it does not match the naming convention",
                    cdmName, slideCode, baseCDMName);
            prefix = "";
        } else {
            prefix = cdmName.substring(0, slideCodeIndex);
        }
        if (StringUtils.isNotBlank(internalLineName)) {
            if (StringUtils.isNotBlank(prefix) && !StringUtils.startsWith(prefix, internalLineName)) {
                LOG.info("Internal line name '{}' and found prefix '{}' do not match in {}",
                         internalLineName, prefix, cdmName);
                prefix = internalLineName + '-';
            } else if (StringUtils.isBlank(prefix)) {
                LOG.info("Use'{}' as prefix for {}", internalLineName, cdmName);
                prefix = internalLineName + '-';
            }
        }
        int sampleRefIndex = baseCDMName.indexOf(sampleRef);
        String channelComponent;
        if (sampleRefIndex == -1) {
            LOG.error("CDM name: {} does not contain the sample ID ({}) and it does not match the naming convention",
                    cdmName, sampleRef);
            // default to separating the name using '-' and use the last component as the channel component
            List<String> nameComponents = Splitter.on('-').splitToList(baseCDMName);
            channelComponent = nameComponents.get(nameComponents.size()-1);
        } else {
            int channelComponentIndex = sampleRefIndex + sampleRef.length() + 1;
            if (channelComponentIndex < baseCDMName.length()) {
                channelComponent = baseCDMName.substring(channelComponentIndex);
            } else {
                // default to separating the name using '-' and use the last component as the channel component
                List<String> nameComponents = Splitter.on('-').splitToList(baseCDMName);
                channelComponent = nameComponents.get(nameComponents.size()-1);
            }
        }
        String channel = StringUtils.removeStartIgnoreCase(StringUtils.removeStartIgnoreCase(channelComponent, "c"), "h");
        LOG.debug("Used {} to extract channel info from {} -> {}", channelComponent, cdmName, channel);
        String mipName = formatSimpleSegmentName(
                prefix +
                        slideCode + '-' +
                        objective + '-' +
                        anatomicalArea + '-' +
                        alignmentSpace + '-' +
                        sampleRef + '-' +
                        "CH" + channel,
                getSegmentIndex(variantName),
                getNameExt(variantName)
        );
        if (slideCodeIndex == -1) {
            // log the final name in case an error was found
            LOG.info("Final targetName for {} -> {} (not entirely based on {})", variantName, mipName, cdmName);
        }
        return mipName;
    }

    private String getSegmentIndex(String name) {
        Matcher segmentIndexMatcher = SEGMENT_INDEX_PATTERN.matcher(name);
        String segmentIndex;
        if (segmentIndexMatcher.find()) {
            segmentIndex = segmentIndexMatcher.group(1);
        } else {
            segmentIndex = "";
        }
        if (StringUtils.isBlank(segmentIndex)) {
            if (args.ignoreMissedSegmentation) {
                return "";
            } else {
                throw new IllegalArgumentException("Segment index not found or empty in " + name);
            }
        } else return segmentIndex;
    }

    private String getNameExt(String name) {
        Matcher nameExtMatcher = FILENAME_EXT_PATTERN.matcher(name);
        if (nameExtMatcher.find()) {
            return nameExtMatcher.group(1);
        } else {
            return "";
        }
    }

    private String formatSimpleSegmentName(String segmentName, String segmentIndex, String imageExt) {
        if (StringUtils.isNotBlank(segmentIndex)) {
            return String.format("%s-%s_CDM%s", segmentName, segmentIndex, imageExt);
        } else {
            return String.format("%s_CDM%s", segmentName, imageExt);
        }
    }

    private void copyFileData(FileData fileData, Path dest) {
        InputStream fdStream;
        try {
            FSUtils.createDirs(dest.getParent());
            fdStream = NeuronMIPUtils.openInputStream(fileData);
            if (fdStream == null) {
                LOG.warn("{} data not found", fileData);
                return;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            LOG.debug("cp {} {}", fileData, dest);
            if (args.forceFlag)
                Files.copy(fdStream, dest, StandardCopyOption.REPLACE_EXISTING);
            else
                Files.copy(fdStream, dest);
        } catch (Exception e) {
            LOG.error("Error copying {} -> {}", fileData, dest, e);
        } finally {
            try {
                fdStream.close();
            } catch (IOException ignore) {}
        }
    }

    private void simulateCopyFileData(FileData fileData, Path dest) {
        LOG.info("cp {} {}", fileData, dest);
        if (!NeuronMIPUtils.exists(fileData)) {
            LOG.error("Source file data {} not found", fileData);
        }
        if (Files.exists(dest)) {
            LOG.warn("Destination file: {} already exists", dest);
        }
    }
}
