package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.janelia.colormipsearch.tools.MIPInfo;
import org.janelia.colormipsearch.tools.MIPsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class ExtractColorMIPsMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractColorMIPsMetadata.class);
    private static final int MAX_SEGMENTED_DATA_DEPTH = 5;

    // since right now there'sonly one EM library just use its name to figure out how to handle the color depth mips metadata
    private static final String NO_CONSENSUS = "No Consensus";
    private static final int DEFAULT_PAGE_LENGTH = 10000;

    private static class MainArgs {
        @Parameter(names = "-h", description = "Display the help message", help = true, arity = 0)
        private boolean displayHelpMessage = false;
    }

    private static class Args {
        @Parameter(names = {"--jacs-url", "--data-url", "--jacsURL"},
                description = "JACS data service base URL", required = true)
        private String dataServiceURL;

        @Parameter(names = {"--config-url"}, description = "Config URL that contains the library name mapping")
        private String libraryMappingURL = "http://config.int.janelia.org/config/cdm_libraries";

        @Parameter(names = {"--authorization"}, description = "JACS authorization - this is the value of the authorization header")
        private String authorization;

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space")
        private String alignmentSpace = "JRC2018_Unisex_20x_HR";

        @Parameter(names = {"--libraries", "-l"},
                description = "Which libraries to extract such as {flyem_hemibrain, flylight_gen1_gal4, flylight_gen1_lexa, flylight_gen1_mcfo_case_1, flylight_splitgal4_drivers}. " +
                        "The format is <libraryName>[:<offset>[:<length>]]",
                required = true, variableArity = true, converter = ListArg.ListArgConverter.class)
        private List<ListArg> libraries;

        @Parameter(names = "--included-libraries", variableArity = true, description = "If set, MIPs should also be in all these libraries")
        private Set<String> includedLibraries;

        @Parameter(names = "--excluded-libraries", variableArity = true, description = "If set, MIPs should not be part of any of these libraries")
        private Set<String> excludedLibraries;

        @Parameter(names = {"--datasets"}, description = "Which datasets to extract", variableArity = true)
        private List<String> datasets;

        @Parameter(names = {"--segmented-mips-base-dir"}, description = "The base directory for segmented MIPS")
        private String segmentedMIPsBaseDir;

        @Parameter(names = {"--output-directory", "-od"}, description = "Output directory", required = true)
        private String outputDir;

        @Parameter(names = {"--skeletons-directory", "-emdir"}, description = "Em skeletons sub-directory")
        private String skeletonsOutput = "by_body";

        @Parameter(names = {"--lines-directory", "-lmdir"}, description = "LM lines sub-directory")
        private String linesOutput = "by_line";

        @Parameter(names = "--include-mips-with-missing-urls", description = "Include MIPs that do not have a valid URL", arity = 0)
        private boolean includeMIPsWithNoPublishedURL;

        @Parameter(names = "--include-mips-without-publishing-name", description = "Bitmap flaf for include MIPs without publishing name: " +
                "0x0 - if either the publishing name is missing or the publishedToStaging is not set the image is not included; " +
                "0x1 - the mip is included even if publishing name is not set; " +
                "0x2 - the mip is included even if publishedToStaging is not set")
        private int includeMIPsWithoutPublisingName = 0;

        @Parameter(names = "--segmented-image-handling", description = "Bit field that specifies how to handle segmented images - " +
                "0 - lookup segmented images but if none is found include the original, " +
                "0x1 - include the original MIP but only if a segmented image exists, " +
                "0x2 - include only segmented image if it exists, " +
                "0x4 - include both the original MIP and all its segmentations")
        private int segmentedImageHandling = 0;

        @Parameter(names = "-h", description = "Display the help message", help = true, arity = 0)
        private boolean displayHelpMessage = false;
    }

    @Parameters(commandDescription = "Group color depth mips")
    private static class GroupCDMIPArgs {
        @ParametersDelegate
        private final Args argsDelegate;

        GroupCDMIPArgs(Args argsDelegate) {
            this.argsDelegate = argsDelegate;
        }
    }

    @Parameters(commandDescription = "Prepare the input arguments for color depth search")
    private static class PrepareColorDepthSearchArgs {
        @ParametersDelegate
        private final Args argsDelegate;

        @Parameter(names = {"--excluded-mips"}, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "Comma-delimited list of JSON configs containing mips to be excluded from the requested list")
        private List<ListArg> excludedMIPs;

        @Parameter(names = {"--output-filename"}, description = "Output file name")
        private String outputFileName;

        PrepareColorDepthSearchArgs(Args argsDelegate) {
            this.argsDelegate = argsDelegate;
        }
    }

    private final WebTarget serverEndpoint;
    private final String credentials;
    private final ObjectMapper mapper;

    private ExtractColorMIPsMetadata(String serverURL, String credentials) {
        this.serverEndpoint = createHttpClient().target(serverURL);
        this.credentials = credentials;
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private Map<String, String> retrieveLibraryNameMapping(String configURL) {
        Response response = createHttpClient().target(configURL).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalStateException("Invalid response from " + configURL + " -> " + response);
        }
        Map<String, Object> configJSON = response.readEntity(new GenericType<>(new TypeReference<Map<String, Object>>() {}.getType()));
        Object configEntry = configJSON.get("config");
        if (!(configEntry instanceof Map)) {
            LOG.error("Config entry from {} is null or it's not a map", configJSON);
            throw new IllegalStateException("Config entry not found");
        }
        Map<String, String> cdmLibraryNamesMapping = new HashMap<>();
        cdmLibraryNamesMapping.putAll((Map<String, String>)configEntry);
        LOG.info("Using {} for mapping library names", cdmLibraryNamesMapping);
        return cdmLibraryNamesMapping;
    }

    private void writeColorDepthMetadata(String alignmentSpace,
                                         ListArg libraryArg,
                                         List<String> datasets,
                                         Set<String> includedLibraries,
                                         Set<String> excludedLibraries,
                                         Map<String, String> libraryNamesMapping,
                                         boolean includeMIPsWithNoImageURL,
                                         int missingPublishingHandling,
                                         Path outputPath) {
        // get color depth mips from JACS for the specified alignmentSpace and library
        int cdmsCount = countColorDepthMips(alignmentSpace, libraryArg.input, datasets);
        LOG.info("Found {} entities in library {} with alignment space {}{}", cdmsCount, libraryArg.input, alignmentSpace, CollectionUtils.isNotEmpty(datasets) ? " for datasets " + datasets : "");
        int to = libraryArg.length > 0 ? Math.min(libraryArg.offset + libraryArg.length, cdmsCount) : cdmsCount;

        Function<ColorDepthMIP, String> libraryNameExtractor = cmip -> {
            String internalLibraryName = cmip.findLibrary(libraryArg.input);
            if (StringUtils.isBlank(internalLibraryName)) {
                return null;
            } else {
                String displayLibraryName = libraryNamesMapping.get(internalLibraryName);
                if (StringUtils.isBlank(displayLibraryName)) {
                    LOG.warn("No name mapping found {}", internalLibraryName);
                    return null;
                } else {
                    return displayLibraryName;
                }
            }
        };

        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            LOG.error("Error creating the output directory for {}", outputPath, e);
        }

        Map<String, List<String>> cdmNameToMIPIdForDupCheck = new HashMap<>();
        for (int pageOffset = libraryArg.offset; pageOffset < to; pageOffset += DEFAULT_PAGE_LENGTH) {
            int pageSize = Math.min(DEFAULT_PAGE_LENGTH, to - pageOffset);
            List<ColorDepthMIP> cdmipsPage = retrieveColorDepthMipsWithSamples(alignmentSpace, libraryArg, datasets, pageOffset, pageSize);
            LOG.info("Process {} entries from {} to {} out of {}", cdmipsPage.size(), pageOffset, pageOffset + pageSize, cdmsCount);
            Map<String, List<ColorDepthMetadata>> resultsByLineOrSkeleton = cdmipsPage.stream()
                    .filter(cdmip -> includeMIPsWithNoImageURL || hasPublishedImageURL(cdmip))
                    .filter(cdmip -> checkMIPLibraries(cdmip, includedLibraries, excludedLibraries))
                    .filter(cdmip -> isEmLibrary(libraryArg.input) || (hasSample(cdmip) && hasConsensusLine(cdmip) && hasPublishedName(missingPublishingHandling, cdmip)))
                    .map(cdmip -> isEmLibrary(libraryArg.input) ? asEMBodyMetadata(cdmip, libraryNameExtractor) : asLMLineMetadata(cdmip, libraryNameExtractor))
                    .filter(cdmip -> StringUtils.isNotBlank(cdmip.getPublishedName()))
                    .peek(cdmip -> {
                        String cdmName = cdmip.getCdmName();
                        if (StringUtils.isNotBlank(cdmName)) {
                            List<String> mipIds = cdmNameToMIPIdForDupCheck.get(cdmName);
                            if (mipIds == null) {
                                cdmNameToMIPIdForDupCheck.put(cdmName, ImmutableList.of(cdmip.getId()));
                            } else {
                                if (!mipIds.contains(cdmip.getId())) {
                                    cdmNameToMIPIdForDupCheck.put(
                                            cdmName,
                                            ImmutableList.<String>builder().addAll(mipIds).add(cdmip.getId()).build());
                                }
                            }
                        }
                    })
                    .filter(cdmip -> {
                        String cdmName = cdmip.getCdmName();
                        if (StringUtils.isBlank(cdmName)) {
                            return true;
                        } else {
                            cdmNameToMIPIdForDupCheck.get(cdmName);
                            List<String> mipIds = cdmNameToMIPIdForDupCheck.get(cdmName);
                            int mipIndex = mipIds.indexOf(cdmip.getId());
                            if (mipIndex == 0) {
                                return true;
                            } else {
                                LOG.info("Not keeping {} because it is a duplicate of {}", cdmip.getId(), mipIds.get(0));
                                return false;
                            }
                        }
                    })
                    .collect(Collectors.groupingBy(
                            cdmip -> cdmip.getPublishedName(),
                            Collectors.toList()));
            // write the results to the output
            resultsByLineOrSkeleton
                    .forEach((lineOrSkeletonName, results) -> writeColorDepthMetadata(outputPath.resolve(lineOrSkeletonName + ".json"), results))
            ;
        }
    }

    private ColorDepthMetadata asLMLineMetadata(ColorDepthMIP cdmip, Function<ColorDepthMIP, String> libraryNameExtractor) {
        String libraryName = libraryNameExtractor.apply(cdmip);
        ColorDepthMetadata cdMetadata = new ColorDepthMetadata();
        cdMetadata.setId(cdmip.id);
        cdMetadata.setLibraryName(libraryName);
        cdMetadata.filepath = cdmip.filepath;
        cdMetadata.setImageUrl(cdmip.publicImageUrl);
        cdMetadata.setThumbnailUrl(cdmip.publicThumbnailUrl);
        cdMetadata.sourceImageRef = cdmip.sourceImageRef;
        if (cdmip.sample != null) {
            cdMetadata.setPublishedToStaging(cdmip.sample.publishedToStaging);
            cdMetadata.setLMLinePublishedName(cdmip.sample.publishingName);
            cdMetadata.addAttr("Slide Code", cdmip.sample.slideCode);
            cdMetadata.addAttr("Gender", cdmip.sample.gender);
            cdMetadata.addAttr("Mounting Protocol", cdmip.sample.mountingProtocol);
        } else {
            populateCDMetadataFromCDMIPName(cdmip, cdMetadata);
        }
        cdMetadata.addAttr("Anatomical Area", cdmip.anatomicalArea);
        cdMetadata.addAttr("Alignment Space", cdmip.alignmentSpace);
        cdMetadata.addAttr("Objective", cdmip.objective);
        cdMetadata.addAttr("Library", libraryName);
        cdMetadata.addAttr("Channel", cdmip.channelNumber);
        return cdMetadata;
    }

    private int getChannel(ColorDepthMetadata cdMetadata) {
        String channel = cdMetadata.getAttr("Channel");
        if (StringUtils.isNotBlank(channel)) {
            return Integer.parseInt(channel) - 1; // mip channels are 1 based so make it 0 based
        } else {
            return -1;
        }
    }

    private int extractChannelFromSegmentedImageName(String imageName) {
        Pattern regExPattern = Pattern.compile("_ch?(\\d+)_", Pattern.CASE_INSENSITIVE);
        Matcher chMatcher = regExPattern.matcher(imageName);
        if (chMatcher.find()) {
            String channel = chMatcher.group(1);
            return Integer.parseInt(channel);
        } else {
            return -1;
        }
    }

    private String extractObjectiveFromSegmentedImageName(String imageName) {
        Pattern regExPattern = Pattern.compile("_([0-9]+x)_", Pattern.CASE_INSENSITIVE);
        Matcher objectiveMatcher = regExPattern.matcher(imageName);
        if (objectiveMatcher.find()) {
            return objectiveMatcher.group(1);
        } else {
            return null;
        }
    }

    private boolean matchMIPChannelWithSegmentedImageChannel(int mipChannel, int segmentImageChannel) {
        if (mipChannel == -1 && segmentImageChannel == -1) {
            return true;
        } else if (mipChannel == -1)  {
            LOG.warn("No channel info found in the mip");
            return true;
        } else if (segmentImageChannel == -1) {
            LOG.warn("No channel info found in the segmented image");
            return true;
        } else {
            return mipChannel == segmentImageChannel;
        }
    }

    private boolean matchMIPObjectiveWithSegmentedImageObjective(String mipObjective, String segmentImageObjective) {
        if (StringUtils.isBlank(mipObjective) && StringUtils.isBlank(segmentImageObjective)) {
            return true;
        } else if (StringUtils.isBlank(mipObjective) )  {
            LOG.warn("No objective found in the mip");
            return false;
        } else if (StringUtils.isBlank(segmentImageObjective)) {
            // if the segmented image does not have objective match it against every image
            return true;
        } else {
            return StringUtils.equalsIgnoreCase(mipObjective, segmentImageObjective);
        }
    }

    private boolean checkMIPLibraries(ColorDepthMIP cdmip, Set<String> includedLibraries, Set<String> excludedLibraries) {
        if (CollectionUtils.isNotEmpty(includedLibraries)) {
            if (!cdmip.libraries.containsAll(includedLibraries)) {
                return false;
            }
        }
        if (CollectionUtils.isNotEmpty(excludedLibraries)) {
            return cdmip.libraries.stream()
                    .filter(l -> excludedLibraries.contains(l))
                    .count() == 0;
        }
        return true;
    }

    private boolean isEmLibrary(String lname) {
        return lname != null && StringUtils.containsIgnoreCase(lname, "flyem") && StringUtils.containsIgnoreCase(lname, "hemibrain");
    }

    private boolean hasSample(ColorDepthMIP cdmip) {
        return cdmip.sample != null;
    }

    private boolean hasConsensusLine(ColorDepthMIP cdmip) {
        return !StringUtils.equalsIgnoreCase(NO_CONSENSUS, cdmip.sample.line);
    }

    private boolean hasPublishedName(int missingPublishingHandling,  ColorDepthMIP cdmip) {
        String publishingName = cdmip.sample.publishingName;
        if (cdmip.sample.publishedToStaging && StringUtils.isNotBlank(publishingName) && !StringUtils.equalsIgnoreCase(NO_CONSENSUS, publishingName)) {
            return true;
        } else {
            if (missingPublishingHandling == 0) {
                return false;
            } else {
                // missingPublishingHandling values:
                //      0x1 - ignore missing name
                //      0x2 - ignore missing published flag
                return (StringUtils.isNotBlank(publishingName) &&
                        !StringUtils.equalsIgnoreCase(NO_CONSENSUS, publishingName) || (missingPublishingHandling & 0x1) != 0) &&
                        (cdmip.sample.publishedToStaging || (missingPublishingHandling & 0x2) != 0);

            }
        }
    }

    private boolean hasPublishedImageURL(ColorDepthMIP cdmip) {
        return StringUtils.isNotBlank(cdmip.publicImageUrl);
    }

    private void populateCDMetadataFromCDMIPName(ColorDepthMIP cdmip, ColorDepthMetadata cdMetadata) {
        List<String> mipNameComponents = Splitter.on('-').splitToList(cdmip.name);
        String line = mipNameComponents.size() > 0 ? mipNameComponents.get(0) : cdmip.name;
        // attempt to remove the PI initials
        int piSeparator = StringUtils.indexOf(line, '_');
        String lineID;
        if (piSeparator == -1) {
            lineID = line;
        } else {
            lineID = line.substring(piSeparator + 1);
        }
        cdMetadata.setLMLinePublishedName(StringUtils.defaultIfBlank(lineID, "Unknown"));
        cdMetadata.addAttr("Line", line);
        String slideCode = mipNameComponents.size() > 1 ? mipNameComponents.get(1) : null;
        cdMetadata.addAttr("Slide Code", slideCode);
    }

    private ColorDepthMetadata asEMBodyMetadata(ColorDepthMIP cdmip, Function<ColorDepthMIP, String> libraryNameExtractor) {
        String libraryName = libraryNameExtractor.apply(cdmip);
        ColorDepthMetadata cdMetadata = new ColorDepthMetadata();
        cdMetadata.setId(cdmip.id);
        cdMetadata.setLibraryName(libraryName);
        cdMetadata.filepath = cdmip.filepath;
        cdMetadata.setImageUrl(cdmip.publicImageUrl);
        cdMetadata.setThumbnailUrl(cdmip.publicThumbnailUrl);
        cdMetadata.setEMSkeletonPublishedName(extractEMSkeletonIdFromName(cdmip.name));
        cdMetadata.addAttr("Library", libraryName);
        return cdMetadata;
    }

    private String extractEMSkeletonIdFromName(String name) {
        List<String> mipNameComponents = Splitter.on('_').splitToList(name);
        return mipNameComponents.size() > 0 ? mipNameComponents.get(0) : null;
    }

    /**
     * Preconditions results.isNotEmpty
     *
     * @param outputPath
     * @param results
     */
    private void writeColorDepthMetadata(Path outputPath, List<ColorDepthMetadata> results) {
        if (Files.exists(outputPath)) {
            RandomAccessFile rf;
            OutputStream outputStream;
            try {
                LOG.debug("Append to {}", outputPath);
                rf = new RandomAccessFile(outputPath.toFile(), "rw");
                long rfLength = rf.length();
                // position FP after the end of the last item
                // this may not work on Windows because of the new line separator
                // - so on windows it may need to rollback more than 4 chars
                rf.seek(rfLength - 4);
                outputStream = Channels.newOutputStream(rf.getChannel());
            } catch (IOException e) {
                LOG.error("Error creating the output stream to be appended for {}", outputPath, e);
                return;
            }
            try {
                // FP is positioned at the end of the last element
                long endOfLastItemPos = rf.getFilePointer();
                outputStream.write(", ".getBytes()); // write the separator for the next array element
                // append the new elements to the existing results
                JsonGenerator gen = mapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8);
                gen.useDefaultPrettyPrinter();
                gen.writeStartObject(); // just to tell the generator that this is inside of an object which has an array
                gen.writeArrayFieldStart("results");
                gen.writeObject(results.get(0)); // write the first element - it can be any element or dummy object
                // just to fool the generator that there is already an element in the array
                gen.flush();
                // reset the position
                rf.seek(endOfLastItemPos);
                // and now start writing the actual elements
                writeColorDepthArrayValues(gen, results);
                gen.writeEndArray();
                gen.writeEndObject();
                gen.flush();
                long currentPos = rf.getFilePointer();
                rf.setLength(currentPos); // truncate
            } catch (IOException e) {
                LOG.error("Error writing json output for {} outputfile {}", results, outputPath, e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ignore) {
                }
            }
        } else {
            FileOutputStream outputStream;
            try {
                outputStream = new FileOutputStream(outputPath.toFile());
            } catch (FileNotFoundException e) {
                LOG.error("Error opening the outputfile {}", outputPath, e);
                return;
            }
            try {
                JsonGenerator gen = mapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8);
                gen.useDefaultPrettyPrinter();
                gen.writeStartObject();
                gen.writeArrayFieldStart("results");
                writeColorDepthArrayValues(gen, results);
                gen.writeEndArray();
                gen.writeEndObject();
                gen.flush();
            } catch (IOException e) {
                LOG.error("Error writing json output for {} outputfile {}", results, outputPath, e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private void writeColorDepthArrayValues(JsonGenerator gen, List<ColorDepthMetadata> results) {
        for (ColorDepthMetadata item : results) {
            try {
                gen.writeObject(item);
            } catch (IOException e) {
                LOG.error("Error writing {}", item, e);
            }
        }
    }

    private void prepareColorDepthSearchArgs(String alignmentSpace,
                                             ListArg libraryArg,
                                             List<String> datasets,
                                             Set<String> includedLibraries,
                                             Set<String> excludedLibraries,
                                             Set<MIPInfo> excludedMIPs,
                                             Map<String, String> libraryNamesMapping,
                                             boolean includeMIPsWithNoImageURL,
                                             int missingPublishingHandling,
                                             String segmentedMIPsBaseDir,
                                             int segmentedImageHandling,
                                             Path outputPath,
                                             String outputFileName) {
        int cdmsCount = countColorDepthMips(alignmentSpace, libraryArg.input, datasets);
        LOG.info("Found {} entities in library {} with alignment space {}{}", cdmsCount, libraryArg.input, alignmentSpace, CollectionUtils.isNotEmpty(datasets) ? " for datasets " + datasets : "");
        int to = libraryArg.length > 0 ? Math.min(libraryArg.offset + libraryArg.length, cdmsCount) : cdmsCount;

        Function<ColorDepthMIP, String> libraryNameExtractor = cmip -> {
            String internalLibraryName = cmip.findLibrary(libraryArg.input);
            if (StringUtils.isBlank(internalLibraryName)) {
                return null;
            } else {
                String displayLibraryName = libraryNamesMapping.get(internalLibraryName);
                if (StringUtils.isBlank(displayLibraryName)) {
                    LOG.warn("No name mapping found {}", internalLibraryName);
                    return null;
                } else {
                    return displayLibraryName;
                }
            }
        };

        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            LOG.error("Error creating the output directory for {}", outputPath, e);
        }

        FileOutputStream outputStream;
        try {
            String outputName;
            String outputBasename = StringUtils.defaultIfBlank(outputFileName, libraryArg.input);
            if (libraryArg.offset > 0 || libraryArg.length > 0) {
                outputName = outputBasename + "-" + libraryArg.offset + "-" + to + ".json";
            } else {
                outputName = outputBasename + ".json";
            }
            Path outputFilePath = outputPath.resolve(outputName);
            LOG.info("Write color depth MIPs to {}", outputFilePath);
            outputStream = new FileOutputStream(outputFilePath.toFile());
        } catch (FileNotFoundException e) {
            LOG.error("Error opening the outputfile {}", outputPath, e);
            return;
        }
        try {
            Pair<String, Map<String, List<String>>> segmentedImages;
            if (isEmLibrary(libraryArg.input)) {
                Pattern skeletonRegExPattern = Pattern.compile("([0-9]+)_.*");
                segmentedImages = getSegmentedImages(skeletonRegExPattern, segmentedMIPsBaseDir);
            } else {
                Pattern slideCodeRegExPattern = Pattern.compile("[-_](\\d\\d\\d\\d\\d\\d\\d\\d_[a-zA-Z0-9]+_[a-zA-Z0-9]+)([-_][mf])?[-_](.+_)ch?(\\d+)_", Pattern.CASE_INSENSITIVE);
                segmentedImages = getSegmentedImages(slideCodeRegExPattern, segmentedMIPsBaseDir);
            }
            LOG.info("Found {} segmented slide codes", segmentedImages.getRight().size());
            JsonGenerator gen = mapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8);
            gen.useDefaultPrettyPrinter();
            gen.writeStartArray();
            for (int pageOffset = libraryArg.offset; pageOffset < to; pageOffset += DEFAULT_PAGE_LENGTH) {
                int pageSize = Math.min(DEFAULT_PAGE_LENGTH, to - pageOffset);
                List<ColorDepthMIP> cdmipsPage = retrieveColorDepthMipsWithSamples(alignmentSpace, libraryArg, datasets, pageOffset, pageSize);
                LOG.info("Process {} entries from {} to {} out of {}", cdmipsPage.size(), pageOffset, pageOffset + pageSize, cdmsCount);
                cdmipsPage.stream()
                        .peek(cdmip -> {
                            if (Files.notExists(Paths.get(cdmip.filepath))) {
                                LOG.warn("No filepath {} found for {} (sample: {}, publishedName: {})", cdmip.filepath, cdmip, cdmip.sampleRef, !hasSample(cdmip) ? "no sample": cdmip.sample.publishingName);
                            }
                        })
                        .filter(cdmip -> includeMIPsWithNoImageURL || hasPublishedImageURL(cdmip))
                        .filter(cdmip -> checkMIPLibraries(cdmip, includedLibraries, excludedLibraries))
                        .filter(cdmip -> isEmLibrary(libraryArg.input) || (hasSample(cdmip) && hasConsensusLine(cdmip) && hasPublishedName(missingPublishingHandling, cdmip)))
                        .map(cdmip -> isEmLibrary(libraryArg.input) ? asEMBodyMetadata(cdmip, libraryNameExtractor) : asLMLineMetadata(cdmip, libraryNameExtractor))
                        .flatMap(cdmip -> findSegmentedMIPs(cdmip, segmentedMIPsBaseDir, segmentedImages, segmentedImageHandling).stream())
                        .map(cdmip -> cdmip.asMIPInfo())
                        .filter(cdmip -> CollectionUtils.isEmpty(excludedMIPs) || !excludedMIPs.contains(cdmip))
                        .forEach(cdmip -> {
                            try {
                                gen.writeObject(cdmip);
                            } catch (IOException e) {
                                LOG.error("Error writing entry for {}", cdmip, e);
                            }
                        });
            }
            gen.writeEndArray();
            gen.flush();
        } catch (IOException e) {
            LOG.error("Error writing json args for library {}", libraryArg, e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException ignore) {
            }
        }
    }

    private List<ColorDepthMetadata> findSegmentedMIPs(ColorDepthMetadata cdmipMetadata, String segmentedImagesBasePath, Pair<String, Map<String, List<String>>> segmentedImages, int segmentedImageHandling) {
        if (StringUtils.isBlank(segmentedImagesBasePath)) {
            return Collections.singletonList(cdmipMetadata);
        } else {
            List<ColorDepthMetadata> segmentedCDMIPs = lookupSegmentedImages(cdmipMetadata, segmentedImagesBasePath, segmentedImages.getLeft(), segmentedImages.getRight());
            if (segmentedImageHandling == 0x1) {
                return segmentedCDMIPs.isEmpty() ? Collections.emptyList() : Collections.singletonList(cdmipMetadata);
            } else if (segmentedImageHandling == 0x2) {
                return segmentedCDMIPs;
            } else if (segmentedImageHandling == 0x4) {
                return Stream.concat(Stream.of(cdmipMetadata), segmentedCDMIPs.stream()).collect(Collectors.toList());
            } else {
                return segmentedCDMIPs.isEmpty() ? Collections.singletonList(cdmipMetadata) : segmentedCDMIPs;
            }
        }
    }

    private List<ColorDepthMetadata> lookupSegmentedImages(ColorDepthMetadata cdmipMetadata, String segmentedDataBasePath, String type, Map<String, List<String>> segmentedImages) {
        String indexingField;
        Predicate<String> segmentedImageMatcher;
        if (isEmLibrary(cdmipMetadata.getLibraryName())) {
            indexingField = cdmipMetadata.getAttr("Body Id");
            Pattern emNeuronStateRegExPattern = Pattern.compile("[0-9]+_([0-9A-Z]*)_.*", Pattern.CASE_INSENSITIVE);
            segmentedImageMatcher = p -> {
                String fn = RegExUtils.replacePattern(Paths.get(p).getFileName().toString(), "\\.\\D*$", "");
                Preconditions.checkArgument(fn.contains(indexingField));
                String cmFN = RegExUtils.replacePattern(Paths.get(cdmipMetadata.filepath).getFileName().toString(), "\\.\\D*$", "");
                String fnState = extractEMNeuronStateFromName(fn, emNeuronStateRegExPattern);
                String cmFNState = extractEMNeuronStateFromName(cmFN, emNeuronStateRegExPattern);
                return StringUtils.isBlank(fnState) && StringUtils.isBlank(cmFNState) ||
                        StringUtils.isNotBlank(cmFNState) && fnState.startsWith(cmFNState); // fnState may be LV or TC which is actually the same as L or T respectivelly so for now this check should work
            };
        } else {
            indexingField = cdmipMetadata.getAttr("Slide Code");
            segmentedImageMatcher = p -> {
                String fn = Paths.get(p).getFileName().toString();
                Preconditions.checkArgument(fn.contains(indexingField));
                int channelFromMip = getChannel(cdmipMetadata);
                int channelFromFN = extractChannelFromSegmentedImageName(fn.replace(indexingField, ""));
                LOG.debug("Compare channel from {} ({}) with channel from {} ({})", cdmipMetadata.filepath, channelFromMip, fn, channelFromFN);
                String objectiveFromMip = cdmipMetadata.getAttr("Objective");
                String objectiveFromFN = extractObjectiveFromSegmentedImageName(fn.replace(indexingField, ""));
                return matchMIPChannelWithSegmentedImageChannel(channelFromMip, channelFromFN) &&
                        matchMIPObjectiveWithSegmentedImageObjective(objectiveFromMip, objectiveFromFN);
            };
        }
        if (segmentedImages.get(indexingField) == null) {
            return Collections.emptyList();
        } else {
            return segmentedImages.get(indexingField).stream()
                    .filter(segmentedImageMatcher)
                    .map(p -> {
                        String sifn = Paths.get(p).getFileName().toString();
                        int scIndex = sifn.indexOf(indexingField);
                        Preconditions.checkArgument(scIndex != -1);
                        ColorDepthMetadata segmentMIPMetadata = new ColorDepthMetadata();
                        cdmipMetadata.copyTo(segmentMIPMetadata);
                        segmentMIPMetadata.segmentedDataBasePath = segmentedDataBasePath;
                        segmentMIPMetadata.type = type;
                        segmentMIPMetadata.segmentFilepath = p;
                        return segmentMIPMetadata;
                    })
                    .collect(Collectors.toList());
        }
    }

    private String extractEMNeuronStateFromName(String name, Pattern emNeuronStatePattern) {
        Matcher matcher = emNeuronStatePattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    private Pair<String, Map<String, List<String>>> getSegmentedImages(Pattern indexingFieldRegExPattern, String segmentedMIPsBaseDir) {
        if (StringUtils.isBlank(segmentedMIPsBaseDir)) {
            return ImmutablePair.of("", Collections.emptyMap());
        } else {
            Path segmentdMIPsBasePath = Paths.get(segmentedMIPsBaseDir);
            if (Files.isDirectory(segmentdMIPsBasePath)) {
                return ImmutablePair.of("file", getSegmentedImagesFromDir(indexingFieldRegExPattern, segmentdMIPsBasePath));
            } else if (Files.isRegularFile(segmentdMIPsBasePath)) {
                return ImmutablePair.of("zipEntry", getSegmentedImagesFromZip(indexingFieldRegExPattern, segmentdMIPsBasePath.toFile()));
            } else {
                return ImmutablePair.of("file", Collections.emptyMap());
            }
        }
    }

    private Map<String, List<String>> getSegmentedImagesFromDir(Pattern indexingFieldRegExPattern, Path segmentedMIPsBasePath) {
        try {
            return Files.find(segmentedMIPsBasePath, MAX_SEGMENTED_DATA_DEPTH,
                    (p, fa) -> fa.isRegularFile())
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.groupingBy(entryName -> {
                        Matcher m = indexingFieldRegExPattern.matcher(entryName);
                        if (m.find()) {
                            return m.group(1);
                        } else {
                            LOG.warn("Indexing field not found in {} using {}", entryName, indexingFieldRegExPattern);
                            throw new IllegalArgumentException("Indexing field code not found in " + entryName);
                        }
                    }));
        } catch (IOException e) {
            LOG.warn("Error scanning {} for segmented images", segmentedMIPsBasePath, e);
            return Collections.emptyMap();
        }
    }

    private Map<String, List<String>> getSegmentedImagesFromZip(Pattern slideCodeRegExPattern, File segmentedMIPsFile) {
        ZipFile segmentedMIPsZipFile;
        try {
            segmentedMIPsZipFile = new ZipFile(segmentedMIPsFile);
        } catch (Exception e) {
            LOG.warn("Error opening segmented mips archive {}", segmentedMIPsFile, e);
            return Collections.emptyMap();
        }
        try {
            return segmentedMIPsZipFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ze -> ze.getName())
                    .collect(Collectors.groupingBy(entryName -> {
                        Matcher m = slideCodeRegExPattern.matcher(entryName);
                        if (m.find()) {
                            return m.group(1);
                        } else {
                            LOG.warn("Slide code not found in {}", entryName);
                            throw new IllegalArgumentException("Slide code not found in " + entryName);
                        }
                    }));
        } finally {
            try {
                segmentedMIPsZipFile.close();
            } catch (IOException ignore) {
            }
        }
    }

    private int countColorDepthMips(String alignmentSpace, String library, List<String> datasets) {
        WebTarget target = serverEndpoint.path("/data/colorDepthMIPsCount")
                .queryParam("libraryName", library)
                .queryParam("alignmentSpace", alignmentSpace)
                .queryParam("dataset", datasets != null ? datasets.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null);
        Response response = createRequestWithCredentials(target.request(MediaType.TEXT_PLAIN)).get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalStateException("Invalid response from " + target + " -> " + response);
        } else {
            return response.readEntity(Integer.class);
        }
    }

    private List<ColorDepthMIP> retrieveColorDepthMipsWithSamples(String alignmentSpace, ListArg libraryArg, List<String> datasets, int offset, int pageLength) {
        return retrieveColorDepthMips(serverEndpoint.path("/data/colorDepthMIPsWithSamples")
                .queryParam("libraryName", libraryArg.input)
                .queryParam("alignmentSpace", alignmentSpace)
                .queryParam("dataset", datasets != null ? datasets.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                .queryParam("offset", offset)
                .queryParam("length", pageLength))
                ;
    }

    private List<ColorDepthMIP> retrieveColorDepthMipsWithoutSamples(String alignmentSpace, ListArg libraryArg, List<String> datasets, int offset, int pageLength) {
        return retrieveColorDepthMips(serverEndpoint.path("/data/colorDepthMIPs")
                .queryParam("libraryName", libraryArg.input)
                .queryParam("alignmentSpace", alignmentSpace)
                .queryParam("dataset", datasets != null ? datasets.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                .queryParam("offset", offset)
                .queryParam("length", pageLength))
                ;
    }

    private List<ColorDepthMIP> retrieveColorDepthMips(WebTarget endpoint) {
        Response response = createRequestWithCredentials(endpoint.request(MediaType.APPLICATION_JSON)).get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalStateException("Invalid response from " + endpoint.getUri() + " -> " + response);
        } else {
            return response.readEntity(new GenericType<>(new TypeReference<List<ColorDepthMIP>>() {
            }.getType()));
        }
    }

    private Invocation.Builder createRequestWithCredentials(Invocation.Builder requestBuilder) {
        if (StringUtils.isNotBlank(credentials)) {
            return requestBuilder.header("Authorization", credentials);
        } else {
            return requestBuilder;
        }
    }

    private Client createHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1");
            TrustManager[] trustManagers = {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String authType) {
                            // Everyone is trusted
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) {
                            // Everyone is trusted
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            sslContext.init(null, trustManagers, new SecureRandom());

            JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    ;

            return ClientBuilder.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .sslContext(sslContext)
                    .hostnameVerifier((s, sslSession) -> true)
                    .register(jsonProvider)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] argv) {
        MainArgs mainArgs = new MainArgs();
        Args args = new Args();
        GroupCDMIPArgs groupCDMIPArgs = new GroupCDMIPArgs(args);
        PrepareColorDepthSearchArgs prepareColorDepthSearchArgs = new PrepareColorDepthSearchArgs(args);
        JCommander cmdline = JCommander.newBuilder()
                .addObject(mainArgs)
                .addCommand("groupMIPS", groupCDMIPArgs)
                .addCommand("prepareCDSArgs", prepareColorDepthSearchArgs)
                .build();

        try {
            cmdline.parse(argv);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder(e.getMessage()).append('\n');
            cmdline.getUsageFormatter().usage(cmdline.getParsedCommand(), sb);
            cmdline.getConsole().println(sb.toString());
            System.exit(1);
        }

        if (mainArgs.displayHelpMessage) {
            cmdline.usage();
            System.exit(0);
        } else if (args.displayHelpMessage && StringUtils.isNotBlank(cmdline.getParsedCommand())) {
            StringBuilder sb = new StringBuilder();
            cmdline.getUsageFormatter().usage(cmdline.getParsedCommand(), sb);
            cmdline.getConsole().println(sb.toString());
            System.exit(0);
        } else if (StringUtils.isBlank(cmdline.getParsedCommand())) {
            StringBuilder sb = new StringBuilder("Missing command\n");
            cmdline.getUsageFormatter().usage(sb);
            cmdline.getConsole().println(sb.toString());
            System.exit(1);
        }

        ExtractColorMIPsMetadata cdmipMetadataExtractor = new ExtractColorMIPsMetadata(args.dataServiceURL, args.authorization);
        Map<String, String> libraryNameMapping = cdmipMetadataExtractor.retrieveLibraryNameMapping(args.libraryMappingURL);

        Set<MIPInfo> excludedMips;
        if (prepareColorDepthSearchArgs.excludedMIPs != null) {
            LOG.info("Read mips to be excluded from the output");
            excludedMips = prepareColorDepthSearchArgs.excludedMIPs.stream()
                    .flatMap(mipsInput -> MIPsUtils.readMIPsFromJSON(
                            mipsInput.input,
                            mipsInput.offset,
                            mipsInput.length,
                            Collections.emptySet(),
                            cdmipMetadataExtractor.mapper).stream())
                    .collect(Collectors.toSet());
            LOG.info("Found {} mips to be excluded from the output", excludedMips.size());
        } else {
            excludedMips = Collections.emptySet();
        }

        args.libraries.forEach(library -> {
            switch (cmdline.getParsedCommand()) {
                case "groupMIPS":
                    Path outputPath;
                    if (cdmipMetadataExtractor.isEmLibrary(library.input)) {
                        outputPath = Paths.get(args.outputDir, args.skeletonsOutput);
                    } else {
                        outputPath = Paths.get(args.outputDir, args.linesOutput);
                    }
                    cdmipMetadataExtractor.writeColorDepthMetadata(
                            args.alignmentSpace,
                            library,
                            args.datasets,
                            args.includedLibraries,
                            args.excludedLibraries,
                            libraryNameMapping,
                            args.includeMIPsWithNoPublishedURL,
                            args.includeMIPsWithoutPublisingName,
                            outputPath
                    );
                    break;
                case "prepareCDSArgs":
                    cdmipMetadataExtractor.prepareColorDepthSearchArgs(
                            args.alignmentSpace,
                            library,
                            args.datasets,
                            args.includedLibraries,
                            args.excludedLibraries,
                            excludedMips,
                            libraryNameMapping,
                            args.includeMIPsWithNoPublishedURL,
                            args.includeMIPsWithoutPublisingName,
                            args.segmentedMIPsBaseDir,
                            args.segmentedImageHandling,
                            Paths.get(args.outputDir),
                            prepareColorDepthSearchArgs.outputFileName
                    );
                    break;
            }
        });
    }

}
