package org.janelia.colormipsearch.cmd_v2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.api_v2.cdmips.AbstractMetadata;
import org.janelia.colormipsearch.api_v2.cdmips.MIPMetadata;
import org.janelia.colormipsearch.api_v2.cdmips.MIPsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
class CopyColorDepthMIPVariantsCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(CopyColorDepthMIPVariantsCmd.class);

    @Parameters(commandDescription = "Copy MIPs variants")
    static class CopyColorDepthMIPVariantsArgs extends AbstractCmdArgs {
        @Parameter(names = {"--input", "-i"}, required = true, converter = ListArg.ListArgConverter.class,
                description = "JSON input MIPs")
        ListArg inputMIPs;

        @Parameter(names = {"--mipsFilter"}, variableArity = true, description = "Filter for i input mips")
        Set<String> mipsFilter;

        @Parameter(names = {"--targetDirectory"}, description = "Input image file(s) start index")
        String targetFolder;

        @Parameter(names = {"-n"}, description = "Only show what the command is supposed to do")
        boolean simulateFlag;

        @Parameter(names = {"--injective-variants"}, variableArity = true,
                description = "These are variant types that only have at most one image for each MIP ID")
        Set<String> injectiveVariants = new HashSet<>();

        @DynamicParameter(names = "-variantMapping", description = "Variants mapping")
        Map<String, String> variantMapping = new HashMap<>();

        @ParametersDelegate
        final CommonArgs commonArgs;

        CopyColorDepthMIPVariantsArgs(CommonArgs commonArgs) {
            this.commonArgs = commonArgs;
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

    private final CopyColorDepthMIPVariantsArgs args;

    CopyColorDepthMIPVariantsCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new CopyColorDepthMIPVariantsArgs(commonArgs);
    }

    @Override
    CopyColorDepthMIPVariantsArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        if (!args.simulateFlag) {
            CmdUtils.createOutputDirs(args.getOutputDir());
        }
        copyAllMIPsVariants(args);
    }

    private void copyAllMIPsVariants(CopyColorDepthMIPVariantsArgs args) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<MIPMetadata> inputMips = MIPsUtils.readMIPsFromJSON(
                args.inputMIPs.input,
                args.inputMIPs.offset,
                args.inputMIPs.length,
                CommonArgs.toLowerCase(args.mipsFilter),
                mapper
        );

        Map<String, List<MIPMetadata>> inputMIPsGroupedByID = inputMips.stream()
                .collect(Collectors.groupingBy(AbstractMetadata::getId, Collectors.toList()));
        LOG.info("Copy variants for {} mips", inputMIPsGroupedByID.size());

        Path outputPath = args.getOutputDir();
        if (outputPath == null) {
            LOG.info("No destination path has been specified");
            return;
        }
        BiConsumer<MIPMetadata,  Path> copyMIPVariantAction;
        if (args.simulateFlag) {
            copyMIPVariantAction = (variantMIP, target) -> LOG.info("cp {} {}", variantMIP, target);
        } else {
            copyMIPVariantAction = this::copyMIPVariant;
        }
        inputMIPsGroupedByID.entrySet().stream().parallel()
                .forEach(me -> {
                    Map<String, Integer> mipsCountsByVariantType = me.getValue().stream()
                            .flatMap(mip -> mip.getVariantTypes().stream()
                                    .filter(vt -> args.variantMapping.get(vt) != null)
                                    .map(vt -> ImmutablePair.of(vt, mip.variantAsMIP(vt))))
                            .collect(Collectors.groupingBy(
                                    ImmutablePair::getLeft,
                                    Collectors.mapping(ImmutablePair::getRight, Collectors.collectingAndThen(Collectors.toSet(), Set::size))));

                    // handle injective variants - an injective variant must be specified by the user as such and also
                    // there should not be more than 1 object per MIP ID for that variant type
                    // for example of such a variant is a gamma modified MIP for better visualization
                    Map<String, MIPMetadata> injectiveVariants = mipsCountsByVariantType.entrySet().stream()
                            .filter(variantTypeCountEntry -> variantTypeCountEntry.getValue() == 1)
                            .map(Map.Entry::getKey)
                            .filter(vt -> args.injectiveVariants.contains(vt))
                            .flatMap(vt -> me.getValue().stream().filter(mip -> mip.hasVariant(vt)).map(mip -> ImmutablePair.of(vt, mip.variantAsMIP(vt))))
                            .collect(Collectors.toMap(
                                    ImmutablePair::getLeft,
                                    ImmutablePair::getRight,
                                    (existingValue, newValue) -> {
                                        if (existingValue.equals(newValue)) {
                                            return existingValue;
                                        } else {
                                            throw new IllegalStateException(String.format("Duplicate key %s", newValue));
                                        }
                                    }));

                    injectiveVariants.forEach((variant, variantMIP) -> {
                                String variantDestination = args.variantMapping.get(variant);
                                copyMIPVariantAction.accept(
                                        variantMIP,
                                        outputPath.resolve(variantDestination)
                                                .resolve(createMIPVariantName(variantMIP, variantMIP, -1))
                                );
                            });

                    // surjective variants
                    Set<String> surjectiveVariantTypes = mipsCountsByVariantType.entrySet().stream()
                            .filter(variantTypeCountEntry -> !injectiveVariants.containsKey(variantTypeCountEntry.getKey()))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());
                    int mipIndex = 1;
                    for (MIPMetadata mip : me.getValue()) {
                        copyMIPVariants(mipIndex, mip, outputPath, args.variantMapping, surjectiveVariantTypes, copyMIPVariantAction);
                        mipIndex++;
                    }
                });
    }

    private void copyMIPVariants(int variantIndex,
                                 MIPMetadata mip,
                                 Path outputPath,
                                 Map<String, String> variantMapping,
                                 Set<String> variantTypes,
                                 BiConsumer<MIPMetadata, Path> action) {
        mip.getVariantTypes().stream()
                .filter(variantTypes::contains)
                .forEach(variant -> {
                    String variantDestination = variantMapping.get(variant);
                    MIPMetadata variantMIP = mip.variantAsMIP(variant);
                    action.accept(
                            variantMIP,
                            outputPath.resolve(variantDestination)
                                    .resolve(createMIPVariantName(mip, variantMIP, variantIndex))
                    );
                });
    }

    private String createMIPVariantName(MIPMetadata mip, MIPMetadata variantMIP, int segmentIndex) {
        String cdmPath = mip.getCdmPath();
        String cdmImageVariantPath = variantMIP.getImagePath();
        String cdmName = Paths.get(cdmPath).getFileName().toString();
        String cdmNameWithoutExt = RegExUtils.replacePattern(cdmName, "\\..*$", "");
        if (StringUtils.endsWith(cdmNameWithoutExt, "_CDM")) {
            String cdmSegmentName = StringUtils.removeEnd(cdmNameWithoutExt, "_CDM");
            if (variantMIP.hasSlideCode()) {
                return createFileNameFromLMMips(variantMIP, cdmSegmentName, segmentIndex, getImageExt(cdmImageVariantPath));
            } else {
                return formatSimpleSegmentName(cdmSegmentName, segmentIndex, getImageExt(cdmImageVariantPath));
            }
        } else {
            String cdmVariantName = Paths.get(cdmImageVariantPath).getFileName().toString();
            String cdmVariantNameWithoutExt = RegExUtils.replacePattern(cdmVariantName, "\\..*$", "");
            return formatSimpleSegmentName(cdmVariantNameWithoutExt, segmentIndex, getImageExt(cdmImageVariantPath));
        }
    }

    private String createFileNameFromLMMips(MIPMetadata mip, String cdmBaseName, int segmentIndex, String ext) {
        List<String> cdmBaseNameComponents = Splitter.on('-').splitToList(cdmBaseName);
        String prefix = cdmBaseNameComponents.get(0); // there should always be at least one component even if there is no hyphen delim
        String slideCode = mip.getSlideCode();
        String objective = getMIPComponent(mip,
                MIPMetadata::getObjective,
                Function.identity(),
                cdmBaseNameComponents,
                2,
                "");
        String area = getMIPComponent(mip,
                MIPMetadata::getAnatomicalArea,
                Function.identity(),
                cdmBaseNameComponents,
                3,
                "");
        String alignmentSpace = mip.getAlignmentSpace();
        String sampleRef = getMIPComponent(mip,
                MIPMetadata::getSampleRef,
                s -> StringUtils.removeStartIgnoreCase(s, "Sample#"),
                cdmBaseNameComponents,
                5,
                "");
        String channel = getMIPComponent(mip,
                MIPMetadata::getChannel,
                s -> StringUtils.removeStartIgnoreCase(
                        StringUtils.removeStartIgnoreCase(s, "c"), "h"
                ),
                cdmBaseNameComponents,
                6,
                "");
        return formatSimpleSegmentName(prefix + '-' +
                slideCode + '-' +
                objective + '-' +
                area + '-' +
                alignmentSpace + '-' +
                sampleRef + '-' +
                "CH" + StringUtils.removeStartIgnoreCase(channel, "c"), segmentIndex, ext);
    }

    private String getMIPComponent(MIPMetadata mip,
                                   Function<MIPMetadata, String> getter,
                                   Function<String, String> fieldTransform,
                                   List<String> comps, int index, String defaultValue) {
        String mipField = getter.apply(mip);
        if (StringUtils.isNotBlank(mipField)) {
            return fieldTransform.apply(mipField);
        } else {
            return fieldTransform.apply(getComponent(comps, index, defaultValue));
        }
    }

    private String getComponent(List<String> comps, int index, String defaultValue) {
        if (index < comps.size()) {
            return comps.get(index);
        } else {
            return defaultValue;
        }
    }

    private String formatSimpleSegmentName(String segmentName, int segmentIndex, String imageExt) {
        if (segmentIndex > 0) {
            return String.format("%s-%02d_CDM%s", segmentName, segmentIndex, imageExt);
        } else {
            return String.format("%s_CDM%s", segmentName, imageExt);
        }
    }

    private String getImageExt(String imagePath) {
        Pattern imageExtPattern = Pattern.compile(".+(\\..*)$");
        Matcher imageExtMatcher = imageExtPattern.matcher(Paths.get(imagePath).getFileName().toString());
        if (imageExtMatcher.find()) {
            return imageExtMatcher.group(1);
        } else {
            return "";
        }
    }

    private void copyMIPVariant(MIPMetadata variantMIP, Path target) {
        try {
            LOG.debug("cp {} {}", variantMIP, target);
            CmdUtils.createOutputDirs(target.getParent());
            Files.copy(Objects.requireNonNull(MIPsUtils.openInputStream(variantMIP)), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOG.error("Error copying {} -> {}", variantMIP, target, e);
        }
    }

}
