package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileDataUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileDataUtils.class);

    private static final Map<Path, Map<String, List<String>>> FILE_NAMES_CACHE = new HashMap<>();

    public static Path asRealPath(String p) {
        try {
            return Paths.get(p).toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path asRealPath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Lookup variants file data. Variants are matched by name - they must have the same name as the
     * searchable variant base name. For faster look up all variant files are cached and indexed using
     * '-' separated components from the filename. The algorithm also limits the number of components
     * used for indexing to maxIndexingComponent or until the lastComponentPattern is found
     * in case these parameters are set.
     *
     * @param variantLocations - locations searched for matching variants
     * @param fastLookup
     * @param maxIndexingComp
     * @param lastComponentPattern
     * @param compSeparators
     * @param variantPattern
     * @return a list of data files. It is a list because there may be more than 1 match
     *         for example if there are both 20x and 63x objectives - they will both match
     *         and these will have to be filtered downstream
     */
    public static List<FileData> lookupVariantFileData(Collection<String> variantLocations, String fastLookup,
                                                       int maxIndexingComp, String lastComponentPattern,
                                                       String compSeparators,
                                                       Pattern variantPattern) {
        if (CollectionUtils.isEmpty(variantLocations)) {
            return Collections.emptyList();
        } else {
            LOG.debug("Lookup variants at: {}", variantLocations);
            return variantLocations.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(Paths::get)
                    .filter(Files::exists)
                    .map(FileDataUtils::asRealPath)
                    .flatMap(variantPath -> {
                        if (Files.isDirectory(variantPath)) {
                            return lookupVariantFileDataInDir(variantPath, fastLookup, maxIndexingComp, lastComponentPattern, compSeparators, variantPattern).stream();
                        } else if (Files.isRegularFile(variantPath)) {
                            return lookupVariantFileDataInArchive(variantPath, fastLookup, maxIndexingComp, lastComponentPattern, compSeparators, variantPattern).stream();
                        } else {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList())
                    ;
        }
    }

    private static List<FileData> lookupVariantFileDataInDir(Path variantPath, String fastLookup,
                                                             int maxIndexingComp, String lastComponentPattern,
                                                             String compSeparators,
                                                             Pattern variantPattern) {
        Map<String, List<String>> variantDirEntries = getDirEntryNames(variantPath, maxIndexingComp, lastComponentPattern, compSeparators);
        return variantDirEntries.getOrDefault(fastLookup, Collections.emptyList()).stream()
                .filter(e -> variantPattern.matcher(Paths.get(e).getFileName().toString()).find())
                .map(FileData::fromString)
                .collect(Collectors.toList())
                ;
    }

    private static List<FileData> lookupVariantFileDataInArchive(Path variantPath, String fastLookup,
                                                                 int maxIndexingComp, String lastComponentPattern,
                                                                 String compSeparators, Pattern variantPattern) {
        Map<String, List<String>> variantArchiveEntries = getZipEntryNames(variantPath, maxIndexingComp, lastComponentPattern, compSeparators);
        return variantArchiveEntries.getOrDefault(fastLookup, Collections.emptyList()).stream()
                .filter(e -> {
                    Matcher variantMatcher = variantPattern.matcher(Paths.get(e).getFileName().toString());
                    return variantMatcher.find();
                })
                .map(en -> FileData.fromComponentsWithCanonicPath(FileData.FileDataType.zipEntry, variantPath.toString(), en))
                .collect(Collectors.toList());
    }

    private static Map<String, List<String>> getZipEntryNames(Path zipPath, int maxIndexingComp, String lastComponentPattern, String compSeparators) {
        if (FILE_NAMES_CACHE.get(zipPath) == null) {
            return cacheZipEntryNames(zipPath, maxIndexingComp, lastComponentPattern, compSeparators);
        } else {
            return FILE_NAMES_CACHE.get(zipPath);
        }
    }

    private static Map<String, List<String>> cacheZipEntryNames(Path zipPath, int maxIndexingComp, String lastComponentPattern, String compSeparators) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(zipPath.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            // build a reverse index for all file entries for a quick lookup
            Map<String, List<String>> zipEntryNames = archiveFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ZipEntry::getName)
                    .flatMap(ze -> getIndexingComponents(Paths.get(ze), maxIndexingComp, lastComponentPattern, compSeparators)
                            .stream()
                            .map(ic -> ImmutablePair.of(ic, ze)))
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

    /**
     * Extract a list of strings that could be used in a reverse index for looking up the full file path.
     * The number of strings used in the reverse index is based
     * either on maxIndexingComponents if the corresponding argument is greater than 0
     * or on lastComponentPattern (if not null). Once any of these conditions is met it stops
     * trying to find any other candidates for the reverse indexing.
     * @param p
     * @param maxIndexingComponents
     * @param lastComponentPattern
     * @param separators
     * @return
     */
    private static List<String> getIndexingComponents(Path p,
                                                      int maxIndexingComponents,
                                                      String lastComponentPattern,
                                                      String separators) {
        String fn = RegExUtils.replacePattern(p.getFileName().toString(), "\\..*$", "");
        String[] fnComps = StringUtils.split(fn, separators);
        List<String> indexingComps = new ArrayList<>();
        List<String> seenComps = new ArrayList<>();
        int currentCompIndex = 0;
        for (String fnComp : fnComps) {
            if (StringUtils.isBlank(fnComp)) {
                LOG.info("Empty name component found in {} [{}] while indexing file entries by their components",
                        p.getFileName(), p);
                continue;
            }
            if (maxIndexingComponents > 0 && currentCompIndex >= maxIndexingComponents) break;
            if (lastComponentPattern != null && fnComp.matches(lastComponentPattern)) break;
            seenComps.add(fnComp);
            indexingComps.add(fnComp);
            indexingComps.add(String.join("'-", seenComps));
            currentCompIndex++;
        }
        if (indexingComps.isEmpty()) {
            throw new IllegalArgumentException("Invalid MIP name found for " + p.getFileName() + "[" + p + "]");
        }
        return indexingComps;
    }

    private static Map<String, List<String>> getDirEntryNames(Path dirPath,
                                                              int maxIndexingComp,
                                                              String lastComponentPattern,
                                                              String compSeparators) {
        if (FILE_NAMES_CACHE.get(dirPath) == null) {
            return cacheDirEntryNames(dirPath, maxIndexingComp, lastComponentPattern, compSeparators);
        } else {
            return FILE_NAMES_CACHE.get(dirPath);
        }
    }

    private static Map<String, List<String>> cacheDirEntryNames(Path dirPath,
                                                                int maxIndexingComponents,
                                                                String lastComponentPattern,
                                                                String componentSeparators) {
        try (Stream<Path> s = Files.find(dirPath, 1, (p, a) -> !a.isDirectory())) {
            Map<String, List<String>> dirEntryNames =
                    s.flatMap(p -> getIndexingComponents(p, maxIndexingComponents, lastComponentPattern, componentSeparators)
                                    .stream()
                                    .map(ic -> ImmutablePair.of(ic, p)))
                    .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(p -> p.getValue().toString(), Collectors.toList())))
                    ;
            FILE_NAMES_CACHE.put(dirPath, dirEntryNames);
            return dirEntryNames;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
