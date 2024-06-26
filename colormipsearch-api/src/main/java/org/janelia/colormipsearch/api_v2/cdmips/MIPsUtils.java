package org.janelia.colormipsearch.api_v2.cdmips;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see org.janelia.colormipsearch.mips.NeuronMIPUtils
 */
@Deprecated
public class MIPsUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MIPsUtils.class);
    private static Map<String, Map<String, List<String>>> ARCHIVE_ENTRIES_CACHE = new HashMap<>();

    /**
     * Load a MIP image from its MIPInfo
     * @param mip
     * @return
     */
    @Nullable
    public static MIPImage loadMIP(@Nullable MIPMetadata mip) {
        long startTime = System.currentTimeMillis();
        if (mip == null) {
            return null;
        } else {
            LOG.trace("Load MIP {}", mip);
            InputStream inputStream;
            try {
                inputStream = openInputStream(mip);
                if (inputStream == null) {
                    return null;
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                return new MIPImage(mip, ImageArrayUtils.readImageArray(mip.getId(), mip.getImageName(), inputStream));
            } catch (Exception e) {
                LOG.error("Error loading {}", mip, e);
                throw new IllegalStateException(e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException ignore) {
                }
                LOG.trace("Loaded MIP {} in {}ms", mip, System.currentTimeMillis() - startTime);
            }
        }
    }

    public static ImageArray<?> getImageArray(@Nullable MIPImage mipImage) {
        return mipImage != null ? mipImage.getImageArray() : null;
    }

    public static MIPMetadata getMIPMetadata(@Nullable MIPImage mipImage) {
        return mipImage != null ? mipImage.getMipInfo() : null;
    }

    public static boolean exists(MIPMetadata mip) {
        if (StringUtils.equalsIgnoreCase("zipEntry", mip.getImageType())) {
            Path archiveFilePath = Paths.get(mip.getImageArchivePath());
            if (Files.isDirectory(archiveFilePath)) {
                return checkFSDir(archiveFilePath, mip);
            } else if (Files.isRegularFile(archiveFilePath)) {
                return checkZipEntry(archiveFilePath, mip);
            } else {
                return false;
            }
        } else {
            Path imageFilePath = Paths.get(mip.getImageName());
            if (StringUtils.isNotBlank(mip.getImageArchivePath())) {
                Path fullImageFilePath = Paths.get(mip.getImageArchivePath()).resolve(imageFilePath);
                return Files.exists(fullImageFilePath) && fullImageFilePath.toFile().length() > 0;
            } else {
                return Files.exists(imageFilePath);
            }
        }
    }

    private static boolean checkFSDir(Path archiveFilePath, MIPMetadata mip) {
        return Files.exists(archiveFilePath.resolve(mip.getImageName()));
    }

    private static boolean checkZipEntry(Path archiveFilePath, MIPMetadata mip) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(archiveFilePath.toFile());
        } catch (IOException e) {
            return false;
        }
        try {
            if (archiveFile.getEntry(mip.getImageName()) != null) {
                return true;
            } else {
                // slightly longer test
                LOG.warn("Full archive scan for {}", mip);
                String imageFn = Paths.get(mip.getImageName()).getFileName().toString();
                return archiveFile.stream()
                        .filter(ze -> !ze.isDirectory())
                        .map(ze -> Paths.get(ze.getName()).getFileName().toString())
                        .filter(fn -> imageFn.equals(fn))
                        .findFirst()
                        .map(fn -> true)
                        .orElse(false);
            }
        } finally {
            try {
                archiveFile.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Nullable
    public static InputStream openInputStream(MIPMetadata mip) throws IOException {
        if (StringUtils.equalsIgnoreCase("zipEntry", mip.getImageType())) {
            Path archiveFilePath = Paths.get(mip.getImageArchivePath());
            if (Files.isDirectory(archiveFilePath)) {
                return openFileStream(archiveFilePath, mip);
            } else if (Files.isRegularFile(archiveFilePath)) {
                return openZipEntryStream(archiveFilePath, mip);
            } else {
                return null;
            }
        } else {
            Path imageFilePath = Paths.get(mip.getImageName());
            if (Files.exists(imageFilePath)) {
                return Files.newInputStream(imageFilePath);
            } else if (StringUtils.isNotBlank(mip.getImageArchivePath())) {
                Path archiveFilePath = Paths.get(mip.getImageArchivePath());
                if (Files.exists(archiveFilePath.resolve(imageFilePath))) {
                    return openFileStream(archiveFilePath, mip);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private static InputStream openFileStream(Path archiveFilePath, MIPMetadata mip) throws IOException {
        return Files.newInputStream(archiveFilePath.resolve(mip.getImageName()));
    }

    private static InputStream openZipEntryStream(Path archiveFilePath, MIPMetadata mip) throws IOException {
        ZipFile archiveFile = new ZipFile(archiveFilePath.toFile());
        ZipEntry ze = archiveFile.getEntry(mip.getImageName());
        if (ze != null) {
            return archiveFile.getInputStream(ze);
        } else {
            LOG.warn("Full archive scan for {}", mip);
            String imageFn = Paths.get(mip.getImageName()).getFileName().toString();
            return archiveFile.stream()
                    .filter(aze -> !aze.isDirectory())
                    .filter(aze -> imageFn.equals(Paths.get(aze.getName()).getFileName().toString()))
                    .findFirst()
                    .map(aze -> getEntryStream(archiveFile, aze))
                    .orElseGet(() -> {
                        try {
                            archiveFile.close();
                        } catch (IOException ignore) {
                        }
                        return null;
                    });
        }
    }

    private static InputStream getEntryStream(ZipFile archiveFile, ZipEntry zipEntry) {
        try {
            return archiveFile.getInputStream(zipEntry);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * MIPVariant can be the corresponding gradient image or a ZGap image that has applied the dilation already.
     * The typical pattern is that the image file name is the same but the path to it has a certain suffix
     * such as '_gradient' or '_20pxRGBMAX'
     * @param mipInfo
     * @param variantType
     * @param mipVariantLocations
     * @param mipVariantTypeSuffixMapping specifies how the mapping changes from the mipInfo to the variant mip
     * @return
     */
    @Nullable
    public static MIPMetadata getMIPVariantInfo(MIPMetadata mipInfo,
                                                String variantType,
                                                List<String> mipVariantLocations,
                                                Function<String, String> mipVariantTypeSuffixMapping,
                                                String mipVariantNameSuffix) {
        if (mipInfo.hasVariant(variantType)) {
            MIPMetadata mipVariant = mipInfo.variantAsMIP(variantType);
            if (mipVariant != null) {
                return mipVariant;
            }
        }
        if (CollectionUtils.isEmpty(mipVariantLocations)) {
            return null;
        } else {
            return mipVariantLocations.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(Paths::get)
                    .map(variantMIPPath -> {
                        if (Files.isDirectory(variantMIPPath)) {
                            return getMIPVariantInfoFromFilePath(
                                    variantMIPPath,
                                    Paths.get(mipInfo.getImageName()),
                                    mipInfo.getCdmName(),
                                    mipVariantTypeSuffixMapping,
                                    mipVariantNameSuffix);
                        } else if (Files.isRegularFile(variantMIPPath) && StringUtils.endsWithIgnoreCase(variantMIPPath.getFileName().toString(), ".zip")) {
                            return getVariantMIPInfoFromZipEntry(
                                    variantMIPPath.toString(),
                                    mipInfo.getImageName(),
                                    mipVariantNameSuffix);
                        } else {
                            return null;
                        }
                    })
                    .filter(variantMIP -> variantMIP != null)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String createMIPEntryName(String name, String suffix, String ext) {
        return name + StringUtils.defaultIfEmpty(suffix, "") + ext;
    }

    @Nullable
    private static MIPMetadata getMIPVariantInfoFromFilePath(Path mipVariantPath,
                                                             Path mipImagePath,
                                                             String sourceCDMName,
                                                             Function<String, String> mipVariantTypeSuffixMapping,
                                                             String mipVariantNameSuffix) {
        Path mipParentPath = mipImagePath.getParent();
        String mipFilenameWithoutExtension = RegExUtils.replacePattern(mipImagePath.getFileName().toString(), "\\..*$", "");
        List<Path> mipVariantPaths;
        if (mipParentPath == null) {
            String sourceMIPNameWithoutExtension = RegExUtils.replacePattern(sourceCDMName, "\\..*$", "");
            mipVariantPaths = Arrays.asList(
                    mipVariantPath.resolve(
                            createMIPEntryName(mipFilenameWithoutExtension, mipVariantNameSuffix,".png")),
                    mipVariantPath.resolve(
                            createMIPEntryName(mipFilenameWithoutExtension, mipVariantNameSuffix,".tif")),
                    mipVariantPath.resolve(
                            createMIPEntryName(mipVariantTypeSuffixMapping.apply(sourceMIPNameWithoutExtension), mipVariantNameSuffix, ".png")), // search variant based on the transformation of the original mip
                    mipVariantPath.resolve(
                            createMIPEntryName(mipVariantTypeSuffixMapping.apply(sourceMIPNameWithoutExtension), mipVariantNameSuffix, ".tiff"))
            );
        } else {
            int nComponents = mipParentPath.getNameCount();
            mipVariantPaths = Stream.concat(
                    IntStream.range(0, nComponents)
                            .map(i -> nComponents - i - 1)
                            .mapToObj(i -> {
                                if (i > 0)
                                    return mipParentPath.subpath(0, i).resolve(mipVariantTypeSuffixMapping.apply(mipParentPath.getName(i).toString())).toString();
                                else
                                    return mipVariantTypeSuffixMapping.apply(mipParentPath.getName(i).toString());
                            }),
                    Stream.of(""))
                    .flatMap(p -> Stream.of(
                            mipVariantPath.resolve(p).resolve(createMIPEntryName(mipFilenameWithoutExtension, mipVariantNameSuffix, ".png")),
                            mipVariantPath.resolve(p).resolve(createMIPEntryName(mipFilenameWithoutExtension, mipVariantNameSuffix, ".tif"))))
                    .collect(Collectors.toList());
        }
        return mipVariantPaths.stream()
                .filter(Files::exists)
                .filter(Files::isRegularFile)
                .findFirst()
                .map(Path::toString)
                .map(mipVariantImagePathname -> {
                    MIPMetadata variantMIP = new MIPMetadata();
                    variantMIP.setCdmPath(mipVariantImagePathname);
                    variantMIP.setImageName(mipVariantImagePathname);
                    return variantMIP;
                })
                .orElse(null);
    }

    public static List<MIPMetadata> readMIPsFromLocalFiles(String mipsLocation, int offset, int length, Set<String> mipsFilter) {
        Path mipsInputPath = Paths.get(mipsLocation);
        if (Files.isDirectory(mipsInputPath)) {
            return readMIPsFromDirectory(mipsInputPath, mipsFilter, offset, length);
        } else if (Files.isRegularFile(mipsInputPath)) {
            // check if the input is an archive (right now only zip is supported)
            if (StringUtils.endsWithIgnoreCase(mipsLocation, ".zip")) {
                // read mips from zip
                return readMIPsFromZipArchive(mipsLocation, mipsFilter, offset, length);
            } else if (ImageArrayUtils.isImageFile(mipsInputPath.getFileName().toString())) {
                // treat the file as a single image file
                String fname = mipsInputPath.getFileName().toString();
                int extIndex = fname.lastIndexOf('.');
                MIPMetadata mipInfo = new MIPMetadata();
                mipInfo.setId(extIndex == -1 ? fname : fname.substring(0, extIndex));
                mipInfo.setImageName(mipsInputPath.toString());
                return Collections.singletonList(mipInfo);
            } else {
                return Collections.emptyList();
            }
        } else {
            LOG.warn("Cannot traverse links for {}", mipsLocation);
            return Collections.emptyList();
        }
    }

    public static List<MIPMetadata> readMIPsFromJSON(String mipsJSONFilename, int offset, int length, Set<String> filter, ObjectMapper mapper) {
        try {
            LOG.info("Reading {}", mipsJSONFilename);
            List<MIPMetadata> content = mapper.readValue(new File(mipsJSONFilename), new TypeReference<List<MIPMetadata>>() {
            });
            if (CollectionUtils.isEmpty(filter)) {
                int from = Math.max(offset, 0);
                int to = length > 0 ? Math.min(from + length, content.size()) : content.size();
                LOG.info("Read {} mips from {} starting at {} to {}", content.size(), mipsJSONFilename, from, to);
                return content.subList(from, to);
            } else {
                LOG.info("Read {} from {} mips", filter, content.size());
                return content.stream()
                        .filter(mip -> filter.contains(mip.getPublishedName().toLowerCase()) || filter.contains(StringUtils.lowerCase(mip.getId())))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LOG.error("Error reading {}", mipsJSONFilename, e);
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    private static MIPMetadata getVariantMIPInfoFromZipEntry(String mipVariantLocation,
                                                             String mipEntryName,
                                                             String mipVariantNameSuffix) {
        // Lookup up entries with the same name with extension tif or png and entries that have the object number suffix removed with the same extensions (tif and png)
        Path mipEntryPath = Paths.get(mipEntryName);
        String mipEntryFilenameWithoutExtension = RegExUtils.replacePattern(mipEntryPath.getFileName().toString(), "\\..*$", "");
        String mipEntryFilenameWithoutObjectNum = RegExUtils.replacePattern(mipEntryFilenameWithoutExtension, "_\\d\\d*$", "");
        Map<String, List<String>> mipVariantArchiveEntries = getZipEntryNames(mipVariantLocation);
        List<String> mipVariantEntryNames = Arrays.asList(
                createMIPEntryName(mipEntryFilenameWithoutExtension, mipVariantNameSuffix, ".png"),
                createMIPEntryName(mipEntryFilenameWithoutExtension, mipVariantNameSuffix, ".tif"),
                createMIPEntryName(mipEntryFilenameWithoutObjectNum, mipVariantNameSuffix, ".png"),
                createMIPEntryName(mipEntryFilenameWithoutObjectNum, mipVariantNameSuffix, ".tif")
        );
        return mipVariantEntryNames.stream()
                .filter(en -> mipVariantArchiveEntries.containsKey(en))
                .flatMap(en -> mipVariantArchiveEntries.get(en).stream())
                .findFirst()
                .map(en -> {
                    MIPMetadata variantMIP = new MIPMetadata();
                    variantMIP.setImageType("zipEntry");
                    variantMIP.setImageArchivePath(mipVariantLocation);
                    variantMIP.setCdmPath(mipVariantLocation + ":" + en);
                    variantMIP.setImageName(en);
                    return variantMIP;
                })
                .orElse(null);
    }

    private static Map<String, List<String>> getZipEntryNames(String zipFilename) {
        if (ARCHIVE_ENTRIES_CACHE.get(zipFilename) == null) {
            return cacheZipEntryNames(zipFilename);
        } else {
            return ARCHIVE_ENTRIES_CACHE.get(zipFilename);
        }
    }

    private static Map<String, List<String>> cacheZipEntryNames(String zipFilename) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(zipFilename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Map<String, List<String>> zipEntryNames = archiveFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ze -> ze.getName())
                    .collect(Collectors.groupingBy(zen -> Paths.get(zen).getFileName().toString(), Collectors.toList()));
            ARCHIVE_ENTRIES_CACHE.put(zipFilename, zipEntryNames);
            return zipEntryNames;
        } finally {
            try {
                archiveFile.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static List<MIPMetadata> readMIPsFromDirectory(Path mipsInputDirectory, Set<String> mipsFilter, int offset, int length) {
        // read mips from the specified folder
        int from = Math.max(offset, 0);
        try {
            List<MIPMetadata> mips = Files.find(mipsInputDirectory, 1, (p, fa) -> fa.isRegularFile())
                    .filter(p -> ImageArrayUtils.isImageFile(p.getFileName().toString()))
                    .filter(p -> {
                        if (CollectionUtils.isEmpty(mipsFilter)) {
                            return true;
                        } else {
                            String fname = p.getFileName().toString();
                            int separatorIndex = StringUtils.indexOf(fname, '_');
                            if (separatorIndex == -1) {
                                return true;
                            } else {
                                return mipsFilter.contains(StringUtils.substring(fname, 0, separatorIndex).toLowerCase());
                            }
                        }
                    })
                    .skip(from)
                    .map(p -> {
                        String fname = p.getFileName().toString();
                        int extIndex = fname.lastIndexOf('.');
                        MIPMetadata mipInfo = new MIPMetadata();
                        mipInfo.setId(extIndex == -1 ? fname : fname.substring(0, extIndex));
                        mipInfo.setImageName(p.toString());
                        return mipInfo;
                    })
                    .collect(Collectors.toList());
            if (length > 0 && length < mips.size()) {
                return mips.subList(0, length);
            } else {
                return mips;
            }
        } catch (IOException e) {
            LOG.error("Error reading content from {}", mipsInputDirectory, e);
            return Collections.emptyList();
        }
    }

    private static List<MIPMetadata> readMIPsFromZipArchive(String mipsArchive, Set<String> mipsFilter, int offset, int length) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(mipsArchive);
        } catch (IOException e) {
            LOG.error("Error opening the archive stream for {}", mipsArchive, e);
            return Collections.emptyList();
        }
        try {
            int from = offset > 0 ? offset : 0;
            List<MIPMetadata> mips = archiveFile.stream()
                    .filter(ze -> ImageArrayUtils.isImageFile(ze.getName()))
                    .filter(ze -> {
                        if (CollectionUtils.isEmpty(mipsFilter)) {
                            return true;
                        } else {
                            String fname = Paths.get(ze.getName()).getFileName().toString();
                            int separatorIndex = StringUtils.indexOf(fname, '_');
                            if (separatorIndex == -1) {
                                return true;
                            } else {
                                return mipsFilter.contains(StringUtils.substring(fname, 0, separatorIndex).toLowerCase());
                            }
                        }
                    })
                    .skip(from)
                    .map(ze -> {
                        String fname = Paths.get(ze.getName()).getFileName().toString();
                        int extIndex = fname.lastIndexOf('.');
                        MIPMetadata mipInfo = new MIPMetadata();
                        mipInfo.setId(extIndex == -1 ? fname : fname.substring(0, extIndex));
                        mipInfo.setImageType("zipEntry");
                        mipInfo.setImageArchivePath(mipsArchive);
                        mipInfo.setCdmPath(ze.getName());
                        mipInfo.setImageName(ze.getName());
                        return mipInfo;
                    })
                    .collect(Collectors.toList());
            if (length > 0 && length < mips.size()) {
                return mips.subList(0, length);
            } else {
                return mips;
            }
        } finally {
            try {
                archiveFile.close();
            } catch (IOException ignore) {
            }
        }

    }

}
