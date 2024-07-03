package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.model.FileData;

public class FileDataUtils {

    private static final Map<Path, Map<String, List<String>>> FILE_NAMES_CACHE = new HashMap<>();

    public static FileData lookupVariantFileData(Collection<String> variantLocations, String fastLookup,
                                                 int maxIndexingComp, String compSeparators,
                                                 Pattern variantPattern) {
        if (CollectionUtils.isEmpty(variantLocations)) {
            return null;
        } else {
            return variantLocations.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(Paths::get)
                    .map(variantPath -> {
                        if (Files.isDirectory(variantPath)) {
                            return lookupVariantFileDataInDir(variantPath, fastLookup, maxIndexingComp, compSeparators, variantPattern);
                        } else if (Files.isRegularFile(variantPath)) {
                            return lookupVariantFileDataInArchive(variantPath, fastLookup, maxIndexingComp, compSeparators, variantPattern);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static FileData lookupVariantFileDataInDir(Path variantPath, String fastLookup, int maxIndexingComp, String compSeparators, Pattern variantPattern) {
        Map<String, List<String>> variantDirEntries = getDirEntryNames(variantPath, maxIndexingComp, compSeparators);
        return variantDirEntries.getOrDefault(fastLookup, Collections.emptyList()).stream()
                .filter(e -> variantPattern.matcher(Paths.get(e).getFileName().toString()).find())
                .findFirst()
                .map(FileData::fromString)
                .orElse(null);
    }

    private static FileData lookupVariantFileDataInArchive(Path variantPath, String fastLookup, int maxIndexingComp, String compSeparators, Pattern variantPattern) {
        Map<String, List<String>> variantArchiveEntries = getZipEntryNames(variantPath, maxIndexingComp, compSeparators);
        return variantArchiveEntries.getOrDefault(fastLookup, Collections.emptyList()).stream()
                .filter(e -> {
                    Matcher variantMatcher = variantPattern.matcher(Paths.get(e).getFileName().toString());
                    return variantMatcher.find();
                })
                .findFirst()
                .map(en -> FileData.fromComponentsWithCanonicPath(FileData.FileDataType.zipEntry, variantPath.toString(), en))
                .orElse(null);
    }

    private static Map<String, List<String>> getZipEntryNames(Path zipPath, int maxIndexingComp, String compSeparators) {
        if (FILE_NAMES_CACHE.get(zipPath) == null) {
            return cacheZipEntryNames(zipPath, maxIndexingComp, compSeparators);
        } else {
            return FILE_NAMES_CACHE.get(zipPath);
        }
    }

    private static Map<String, List<String>> cacheZipEntryNames(Path zipPath, int maxIndexingComp, String compSeparators) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(zipPath.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Map<String, List<String>> zipEntryNames = archiveFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ZipEntry::getName)
                    .flatMap(ze -> getIndexingComponents(Paths.get(ze), maxIndexingComp, compSeparators).map(ic -> ImmutablePair.of(ic, ze)))
                    .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())))
                    ;
            FILE_NAMES_CACHE.put(zipPath, zipEntryNames);
            return zipEntryNames;
        } finally {
            try {
                archiveFile.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static Stream<String> getIndexingComponents(Path p, int maxIndexingComponents, String separators) {
        String fn = RegExUtils.replacePattern(p.getFileName().toString(), "\\..*$", "");
        return Arrays.stream(StringUtils.split(fn, separators)).filter(StringUtils::isNotBlank).limit(maxIndexingComponents);
    }

    private static Map<String, List<String>> getDirEntryNames(Path dirPath, int maxIndexingComp, String compSeparators) {
        if (FILE_NAMES_CACHE.get(dirPath) == null) {
            return cacheDirEntryNames(dirPath, maxIndexingComp, compSeparators);
        } else {
            return FILE_NAMES_CACHE.get(dirPath);
        }
    }

    private static Map<String, List<String>> cacheDirEntryNames(Path dirPath, int maxIndexingComponents, String componentSeparators) {
        try (Stream<Path> s = Files.find(dirPath, 1, (p, a) -> !a.isDirectory())) {
            Map<String, List<String>> dirEntryNames =
                    s.map(p -> {
                        try {
                            return p.toRealPath();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .flatMap(p -> getIndexingComponents(p, maxIndexingComponents, componentSeparators).map(ic -> ImmutablePair.of(ic, p)))
                    .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(p -> p.getValue().toString(), Collectors.toList())))
                    ;
            FILE_NAMES_CACHE.put(dirPath, dirEntryNames);
            return dirEntryNames;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
