package org.janelia.colormipsearch.cmd_v2;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api_v2.cdmips.MIPMetadata;
import org.janelia.colormipsearch.api_v2.cdmips.MIPsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class ReplaceMIPsMetadataAttributesCmd extends AbstractCmd {
    private static final Logger LOG = LoggerFactory.getLogger(ReplaceMIPsMetadataAttributesCmd.class);

    @Parameters(commandDescription = "Replace metadata attributes")
    static class ReplaceMIPsMetadataAttributesArgs extends AbstractCmdArgs {
        @Parameter(names = {"--new-mips-attributes", "-attrs"}, required = true,
                description = "File containing the MIPS with the new attributes")
        String targetMIPsFilename;
        
        @Parameter(names = {"--input-dirs"}, variableArity = true, description = "Directory with JSON files whose image URLs have to be changed")
        List<String> inputDirs;

        @Parameter(names = {"--input-files"}, variableArity = true, description = "JSON file whose image URLs have to be changed")
        List<String> inputFiles;

        @Parameter(names = {"--id-field"}, required = true,
                converter = MappedFieldArg.MappedFieldArgConverter.class,
                description = "Indexing field - field used to search the entity that has the new attribute values. " +
                        "The indexing field must uniquely identify the subset of attributes to be updated - " +
                        "meaning that all entries that match the indexing field must have the same values for the new attributes.")
        MappedFieldArg indexingField;

        @Parameter(names = {"--fields-toUpdate"}, description = "Fields to be updated",
                converter = MappedFieldArg.MappedFieldArgConverter.class, variableArity = true)
        Set<MappedFieldArg> fieldsToUpdate = new HashSet<>();

        @ParametersDelegate
        final CommonArgs commonArgs;

        ReplaceMIPsMetadataAttributesArgs(CommonArgs commonArgs) {
            this.commonArgs = commonArgs;
        }

        @Override
        List<String> validate() {
            List<String> errors = new ArrayList<>();
            boolean inputFound = inputDirs != null || CollectionUtils.isNotEmpty(inputFiles);
            if (!inputFound) {
                errors.add("No input file or directory containing results has been specified");
            }
            return errors;
        }

        Path getOutputDir() {
            if (StringUtils.isNotBlank(commonArgs.outputDir)) {
                return Paths.get(commonArgs.outputDir);
            } else {
                return null;
            }
        }

    }

    private final ReplaceMIPsMetadataAttributesArgs args;

    ReplaceMIPsMetadataAttributesCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        args =  new ReplaceMIPsMetadataAttributesArgs(commonArgs);
    }

    @Override
    ReplaceMIPsMetadataAttributesArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        CmdUtils.createOutputDirs(args.getOutputDir());
        replaceMIPsURLs(args);
    }

    private void replaceMIPsURLs(ReplaceMIPsMetadataAttributesArgs args) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, MIPMetadata> indexedTargetMIPs = MIPsUtils.readMIPsFromJSON(args.targetMIPsFilename, 0, -1, Collections.emptySet(), mapper)
                .stream()
                .collect(Collectors.groupingBy(
                        mipInfo -> getFieldAttrValue(mipInfo, args.indexingField.getField()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                r -> r.get(0)
                        )));

        List<String> inputFileNames;
        if (CollectionUtils.isNotEmpty(args.inputFiles)) {
            inputFileNames = args.inputFiles;
        } else if (CollectionUtils.isNotEmpty(args.inputDirs)) {
            inputFileNames = args.inputDirs.stream()
                    .flatMap(rd -> {
                        try {
                            return Files.find(Paths.get(rd), 1, (p, fa) -> fa.isRegularFile());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } else {
            inputFileNames = Collections.emptyList();
        }
        replaceMIPsMetadataAttributes(
                inputFileNames,
                args.indexingField,
                args.fieldsToUpdate,
                indexedTargetMIPs,
                args.getOutputDir());
    }

    private void replaceMIPsMetadataAttributes(List<String> inputFileNames,
                                               MappedFieldArg indexingField,
                                               Set<MappedFieldArg> attributesToUpdate,
                                               Map<String, MIPMetadata> indexedTargetMIPs,
                                               Path outputDir) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        inputFileNames.stream().parallel()
                .forEach(fn -> {
                    File f = new File(fn);
                    JsonNode jsonContent = readJSONFile(f, mapper);
                    streamJSONNodes(jsonContent).forEach(jsonNode -> {
                        // search the indexing field value in the current node using the corresponding mapped name
                        String id = getFieldValue(jsonNode, indexingField.getFieldMapping());
                        if (id == null) {
                            return; // No <id> field found
                        }
                        MIPMetadata targetMIP = indexedTargetMIPs.get(id);
                        if (targetMIP == null) {
                            LOG.warn("No MIP with new attributes found for {}", id);
                        } else {
                            ObjectNode newAttributesNode = mapper.valueToTree(targetMIP);
                            attributesToUpdate.forEach(a -> replaceMIPsAttributes(id, newAttributesNode, a, jsonNode));
                        }
                    });
                    writeJSONFile(jsonContent, CmdUtils.getOutputFile(outputDir, f), mapper);
                });
    }

    private JsonNode readJSONFile(File f, ObjectMapper mapper) {
        try {
            LOG.info("Reading {}", f);
            return mapper.readTree(f);
        } catch (IOException e) {
            LOG.error("Error reading json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

    private void writeJSONFile(JsonNode content, File f, ObjectMapper mapper) {
        try {
            if (f == null) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(System.out, content);
            } else {
                LOG.info("Writing {}", f);
                mapper.writerWithDefaultPrettyPrinter().writeValue(f, content);
            }
        } catch (IOException e) {
            LOG.error("Error writing json file {}", f, e);
            throw new UncheckedIOException(e);
        }
    }

    private Stream<ObjectNode> streamJSONNodes(JsonNode jsonContent) {
        if (jsonContent.isArray()) {
            return StreamSupport.stream(jsonContent.spliterator(), false)
                    .map(jsonNode -> (ObjectNode) jsonNode);
        } else {
            return jsonContent.findValues("results").stream()
                    .map(jsonNode -> (ArrayNode) jsonNode)
                    .flatMap(resultsNode -> StreamSupport.stream(resultsNode.spliterator(), false))
                    .map(jsonNode -> (ObjectNode) jsonNode);
        }
    }

    private void replaceMIPsAttributes(String id, ObjectNode srcAttributes, MappedFieldArg updatedField, ObjectNode toUpdate) {
        String srcAttributeName = updatedField.getField();
        String attributeValue = getFieldValue(srcAttributes, srcAttributeName);
        if (StringUtils.isNotBlank(attributeValue)) {
            String targetAttributeName = updatedField.getFieldMapping();
            LOG.debug("Setting {} for {} to {}", targetAttributeName, id, attributeValue);
            toUpdate.put(targetAttributeName, attributeValue);
        }
    }

    private String getFieldValue(ObjectNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null) {
            return fieldNode.textValue();
        } else {
            return null;
        }
    }

    /**
     * like getFieldValue, but for MIPs instead of json nodes; only supports
     * a few attributes, the ones plausibly likely to be needed for the purpose of identifying
     * MIPs for indexing
     */
    public String getFieldAttrValue(MIPMetadata mipInfo, String attrName) {
        if (StringUtils.isBlank(attrName)) {
            throw new IllegalArgumentException("Indexing field cannot be empty");
        } else {
            switch(attrName) {
                case "id":
                    return mipInfo.getId();
                case "publishedName":
                    return mipInfo.getPublishedName();
                case "slideCode":
                    return mipInfo.getSlideCode();
                case "imageName":
                    return mipInfo.getImageName();
                default:
                    throw new IllegalArgumentException("Unsupported indexing field: " + attrName);
            }
        }
    }

}
