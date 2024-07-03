package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.base.Preconditions;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.cmd.jacsdata.ColorDepthMIP;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MIPsHandlingUtils {
    private static final Logger LOG = LoggerFactory.getLogger(MIPsHandlingUtils.class);

    static class MIPsStore {
        String storeBasePath;
        FileData.FileDataType storeEntryType; // file or zip entry
        List<String> images;
        Pattern indexingPattern;
    }

    static class MIPStoreEntry {
        String storeBasePath;
        FileData.FileDataType storeEntryType;
        String imagePath;
        Pattern indexingPattern;

        MIPStoreEntry(String storeBasePath, FileData.FileDataType storeEntryType, String imagePath, Pattern indexingPattern) {
            this.storeBasePath = storeBasePath;
            this.storeEntryType = storeEntryType;
            this.imagePath = imagePath;
            this.indexingPattern = indexingPattern;
        }

        String getEntryName() {
            return Paths.get(imagePath).getFileName().toString();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("storeBasePath", storeBasePath)
                    .append("storeEntryType", storeEntryType)
                    .append("imagePath", imagePath)
                    .toString();
        }
    }

    static Map<String, List<MIPStoreEntry>> indexMIPStores(List<MIPsStore> mipStores) {
        return mipStores.stream()
                .flatMap(mipStore -> mipStore.images.stream().map(imagePath -> new MIPStoreEntry(
                        mipStore.storeBasePath,
                        mipStore.storeEntryType,
                        imagePath,
                        mipStore.indexingPattern)))
                .collect(Collectors.groupingBy(me -> {
                    String entryName = RegExUtils.replacePattern(
                            Paths.get(me.imagePath).getFileName().toString(),
                            "\\..*$", "");
                    if (me.indexingPattern != null) {
                        Matcher m = me.indexingPattern.matcher(entryName);
                        if (m.find()) {
                            return m.group(1);
                        } else {
                            LOG.warn("Indexing field could not be extracted from {} - no match found using {}", entryName, me.indexingPattern);
                        }
                    }
                    return entryName;
                }, Collectors.toList()));
    }

    static int extractColorChannelFromMIPName(String mipName, int channelBase) {
        Pattern regExPattern = Pattern.compile("[_-]ch?(\\d+)([_-]|(\\.))", Pattern.CASE_INSENSITIVE);
        Matcher chMatcher = regExPattern.matcher(mipName);
        if (chMatcher.find()) {
            String channel = chMatcher.group(1);
            return Integer.parseInt(channel) - channelBase;
        } else {
            return -1;
        }
    }

    static String extractEMBodyIdFromName(String name) {
        Matcher matcher = emSkeletonRegexPattern().matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    static boolean isEmLibrary(String lname) {
        return lname != null && (
                StringUtils.startsWithIgnoreCase(lname, "flyem") ||
                        StringUtils.startsWithIgnoreCase(lname, "flywire"));
    }

    @SuppressWarnings("unchecked")
    static <N extends AbstractNeuronEntity> List<N> lookupSearchableNeuronImages(N neuronMetadata,
                                                                                 String sourceObjective,
                                                                                 int sourceChannel,
                                                                                 Map<String, List<MIPStoreEntry>> indexedSearchableImages,
                                                                                 boolean matchNeuronState,
                                                                                 int inputImageChannelBase,
                                                                                 boolean useCanonicPath) {
        Predicate<MIPStoreEntry> segmentedImageMatcher;
        if (isEmLibrary(neuronMetadata.getLibraryName())) {
            Pattern emNeuronStateRegExPattern = Pattern.compile("[0-9]+[_-]([0-9A-Z]*)_.*", Pattern.CASE_INSENSITIVE);
            segmentedImageMatcher = mipStoreEntry -> {
                if (matchNeuronState) {
                    String entryName = mipStoreEntry.getEntryName();
                    String sourceCDMFN = Paths.get(neuronMetadata.getComputeFileName(ComputeFileType.SourceColorDepthImage)).getFileName().toString();
                    String cmFN = RegExUtils.replacePattern(sourceCDMFN, "\\.\\D*$", "");
                    String fnState = extractEMNeuronStateFromName(entryName, emNeuronStateRegExPattern);
                    String cmFNState = extractEMNeuronStateFromName(cmFN, emNeuronStateRegExPattern);
                    return StringUtils.isBlank(fnState) && StringUtils.isBlank(cmFNState) ||
                            StringUtils.isNotBlank(cmFNState) && fnState.startsWith(cmFNState); // fnState may be LV or TC which is actually the same as L or T respectivelly so for now this check should work
                } else {
                    return true;
                }
            };
        } else {
            segmentedImageMatcher = mipStoreEntry -> {
                String entryName = mipStoreEntry.getEntryName();
                String entryWithoutNeuronId = entryName.replace(neuronMetadata.getNeuronId(), "");
                int channelFromFN = extractColorChannelFromMIPName(entryWithoutNeuronId, inputImageChannelBase);
                LOG.debug("Compare channel from {} ({}) with channel from {} ({})",
                        neuronMetadata.getComputeFileData(ComputeFileType.SourceColorDepthImage), sourceChannel, mipStoreEntry, channelFromFN);
                String objectiveFromFN = extractObjectiveFromImageName(entryWithoutNeuronId);
                if (channelFromFN == -1) {
                    LOG.info("No channel info found in MIP: {}", mipStoreEntry);
                }
                return matchMIPChannelWithSegmentedImageChannel(sourceChannel, channelFromFN) &&
                        matchMIPObjectiveWithSegmentedImageObjective(sourceObjective, objectiveFromFN);
            };
        }
        List<MIPStoreEntry> neuronSearchableImages = indexedSearchableImages.get(neuronMetadata.getNeuronId());
        if (CollectionUtils.isEmpty(neuronSearchableImages)) {
            return Collections.emptyList();
        } else {
            return neuronSearchableImages.stream()
                    .filter(segmentedImageMatcher)
                    .map(mipStoreEntry -> {
                        N searchableNeuron = (N) neuronMetadata.duplicate();
                        FileData searchableImage;
                        if (useCanonicPath) {
                            searchableImage =  FileData.fromComponentsWithCanonicPath(mipStoreEntry.storeEntryType, mipStoreEntry.storeBasePath, mipStoreEntry.imagePath);
                        } else {
                            searchableImage = FileData.fromComponents(mipStoreEntry.storeEntryType, mipStoreEntry.storeBasePath, mipStoreEntry.imagePath);
                        }
                        searchableNeuron.setComputeFileData(ComputeFileType.InputColorDepthImage, searchableImage);
                        return searchableNeuron;
                    })
                    .collect(Collectors.toList());
        }
    }

    static int getColorChannel(ColorDepthMIP cdmip) {
        int channel = cdmip.channelNumber();
        if (channel > 0) {
            return channel - 1; // mip channels are 1 based so make it 0 based
        } else {
            return -1;
        }
    }

    private static String extractEMNeuronStateFromName(String name, Pattern emNeuronStatePattern) {
        Matcher matcher = emNeuronStatePattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    private static String extractObjectiveFromImageName(String imageName) {
        Pattern regExPattern = Pattern.compile("[_-]([0-9]+x)[_-]", Pattern.CASE_INSENSITIVE);
        Matcher objectiveMatcher = regExPattern.matcher(imageName);
        if (objectiveMatcher.find()) {
            return objectiveMatcher.group(1);
        } else {
            return null;
        }
    }

    private static boolean matchMIPChannelWithSegmentedImageChannel(int mipChannel, int segmentImageChannel) {
        if (mipChannel == -1 && segmentImageChannel == -1) {
            return true;
        } else if (mipChannel == -1) {
            return true;
        } else if (segmentImageChannel == -1) {
            return true;
        } else {
            return mipChannel == segmentImageChannel;
        }
    }

    private static boolean matchMIPObjectiveWithSegmentedImageObjective(String mipObjective, String segmentImageObjective) {
        if (StringUtils.isBlank(mipObjective) && StringUtils.isBlank(segmentImageObjective)) {
            return true;
        } else if (StringUtils.isBlank(mipObjective)) {
            LOG.warn("No objective found in the mip");
            return false;
        } else if (StringUtils.isBlank(segmentImageObjective)) {
            // if the segmented image does not have objective match it against every image
            return true;
        } else {
            return StringUtils.equalsIgnoreCase(mipObjective, segmentImageObjective);
        }
    }

    static List<MIPsStore> listLibraryImageFiles(String library, Collection<String> locations, String ignorePattern, String nameSuffixFilter) {
        Pattern imageIndexingPattern;
        if (StringUtils.isBlank(library)) {
            imageIndexingPattern = null;
        } else if (isEmLibrary(library)) {
            imageIndexingPattern = emSkeletonRegexPattern();
        } else {
            imageIndexingPattern = lmSlideCodeRegexPattern();
        }
        return locations.stream()
                .map(imagesLocation -> {
                    Path imagesBasePath = Paths.get(imagesLocation);
                    if (Files.isDirectory(imagesBasePath)) {
                        return new MIPsStore() {{
                            storeBasePath = imagesBasePath.toString();
                            storeEntryType = FileData.FileDataType.file;
                            images = listImageFilesFromDir(imagesBasePath, ignorePattern, nameSuffixFilter);
                            indexingPattern = imageIndexingPattern;
                        }};
                    } else {
                        // if it's not a dir we expect it to be an archive
                        return new MIPsStore() {{
                            storeBasePath = imagesBasePath.toString();
                            storeEntryType = FileData.FileDataType.zipEntry;
                            images = listImageFilesFromZip(imagesBasePath.toFile(), ignorePattern, nameSuffixFilter);
                            indexingPattern = imageIndexingPattern;
                        }};
                    }
                })
                .collect(Collectors.toList());
    }

    private static Pattern emSkeletonRegexPattern() {
        return Pattern.compile("([0-9]{5,})[_-].*");
    }

    private static Pattern lmSlideCodeRegexPattern() {
        return Pattern.compile("[-_](\\d\\d\\d\\d\\d\\d\\d\\d_[a-zA-Z0-9]+_[a-zA-Z0-9]+)([-_][mf])?[-_](.+[_-])ch?(\\d+)([_-]|(\\.))", Pattern.CASE_INSENSITIVE);
    }

    private static List<String> listImageFilesFromDir(Path baseDir, String ignorePattern, String nameSuffixFilter) {
        try {
            return Files.find(baseDir.toRealPath(), 1,
                            (p, fa) -> fa.isRegularFile())
                    .map(p -> p.getFileName().toString())
                    .filter(entryName -> {
                        if (StringUtils.isBlank(nameSuffixFilter)) {
                            return true;
                        } else {
                            String entryNameWithNoExt = RegExUtils.replacePattern(entryName, "\\.\\D*$", "");
                            return StringUtils.endsWithIgnoreCase(entryNameWithNoExt, nameSuffixFilter);
                        }
                    })
                    .filter(entryName -> {
                        if (StringUtils.isBlank(ignorePattern)) {
                            return true;
                        } else {
                            return !StringUtils.containsIgnoreCase(entryName, ignorePattern);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.warn("Error scanning {} for image files", baseDir, e);
            return Collections.emptyList();
        }
    }

    private static List<String> listImageFilesFromZip(File imagesFileArchive, String ignorePattern, String nameSuffixFilter) {
        ZipFile imagesZipFile;
        try {
            imagesZipFile = new ZipFile(imagesFileArchive);
        } catch (Exception e) {
            LOG.warn("Error opening image archive {}", imagesFileArchive, e);
            return Collections.emptyList();
        }
        try {
            return imagesZipFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ZipEntry::getName)
                    .filter(entryName -> {
                        if (StringUtils.isBlank(nameSuffixFilter)) {
                            return true;
                        } else {
                            String entryNameWithNoExt = RegExUtils.replacePattern(entryName, "\\.\\D*$", "");
                            return StringUtils.endsWithIgnoreCase(entryNameWithNoExt, nameSuffixFilter);
                        }
                    })
                    .filter(entryName -> {
                        if (StringUtils.isBlank(ignorePattern)) {
                            return true;
                        } else {
                            return !StringUtils.containsIgnoreCase(entryName, ignorePattern);
                        }
                    })
                    .collect(Collectors.toList());
        } finally {
            try {
                imagesZipFile.close();
            } catch (IOException ignore) {
            }
        }
    }

}
