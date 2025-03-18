package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.imageprocessing.ImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageArrayUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeuronMIPUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeuronMIPUtils.class);

    @FunctionalInterface
    public interface NeuronImageFileLoader<N extends AbstractNeuronEntity> {
        ImageArray<?> loadImage(N neuron, ComputeFileType computeFileType);
    }

    public static <N extends AbstractNeuronEntity> Map<ComputeFileType, Supplier<ImageArray<?>>> getImageLoaders(N neuron,
                                                                                                                 Set<ComputeFileType> fileTypes,
                                                                                                                 NeuronImageFileLoader<N> singleNeuronImageLoader) {
        return fileTypes.stream()
                .map(cft -> {
                    Pair<ComputeFileType, Supplier<ImageArray<?>>> e =
                            ImmutablePair.of(
                                    cft,
                                    () -> {
                                        LOG.trace("Loading {} variant for {}", cft, neuron);
                                        return singleNeuronImageLoader.loadImage(neuron, cft);
                                    }
                            );
                    return e;
                })
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
                ;
    }

    /**
     * Load a Neuron image from its metadata
     * @param neuronMetadata
     * @param computeFileType
     * @return
     */
    @Nullable
    public static <N extends AbstractNeuronEntity> NeuronMIP<N> loadComputeFile(@Nullable N neuronMetadata, ComputeFileType computeFileType) {
        if (neuronMetadata == null) {
            return null;
        } else {
            LOG.trace("Load MIP {}:{}", neuronMetadata, computeFileType);
            FileData neuronFile = neuronMetadata.getComputeFileData(computeFileType);
            if (neuronFile != null) {
                return new NeuronMIP<>(neuronMetadata, neuronFile, loadImageFromFileData(neuronFile));
            } else {
                return new NeuronMIP<>(neuronMetadata, null, null);
            }
        }
    }

    public static ImageArray<?> loadImageFromFileData(FileData fd) {
        long startTime = System.currentTimeMillis();
        InputStream inputStream;
        try {
            inputStream = openInputStream(fd);
            if (inputStream == null) {
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            return ImageArrayUtils.readImageArray(fd.getName(), fd.getName(), inputStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
            LOG.trace("Loaded image from {} in {}ms", fd, System.currentTimeMillis() - startTime);
        }
    }

    public static boolean hasImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP != null && neuronMIP.hasImageArray();
    }

    public static boolean hasNoImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP == null || neuronMIP.hasNoImageArray();
    }

    public static ImageArray<?> getImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP != null ? neuronMIP.getImageArray() : null;
    }

    public static <N extends AbstractNeuronEntity> N getMetadata(@Nullable NeuronMIP<N> neuronMIP) {
        return neuronMIP != null ? neuronMIP.getNeuronInfo() : null;
    }

    public static boolean exists(FileData fileData) {
        if (fileData == null) {
            return false;
        } else if (fileData.getDataType() == FileData.FileDataType.zipEntry) {
            Path dataPath = Paths.get(fileData.getFileName());
            if (Files.isDirectory(dataPath)) {
                return checkFile(dataPath.resolve(fileData.getEntryName()));
            } else if (Files.isRegularFile(dataPath)) {
                return checkZipEntry(dataPath, fileData.getEntryName());
            } else {
                return false;
            }
        } else {
            Path dataPath = Paths.get(fileData.getFileName());
            if (Files.isDirectory(dataPath)) {
                return checkFile(dataPath.resolve(fileData.getEntryName()));
            } else if (Files.isRegularFile(dataPath)) {
                return checkFile(dataPath);
            } else {
                return false;
            }
        }
    }

    private static boolean checkFile(Path fp) {
        return Files.exists(fp);
    }

    private static boolean checkZipEntry(Path archiveFilePath, String entryName) {
        ZipFile archiveFile;
        try {
            archiveFile = new ZipFile(archiveFilePath.toFile());
        } catch (IOException e) {
            return false;
        }
        try {
            if (archiveFile.getEntry(entryName) != null) {
                return true;
            } else {
                // slightly longer test
                LOG.warn("Full {} archive scan for {}", archiveFilePath, entryName);
                String imageFn = Paths.get(entryName).getFileName().toString();
                return archiveFile.stream()
                        .filter(ze -> !ze.isDirectory())
                        .map(ze -> Paths.get(ze.getName()).getFileName().toString())
                        .anyMatch(imageFn::equals);
            }
        } finally {
            try {
                archiveFile.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Nullable
    public static InputStream openInputStream(FileData fileData) throws IOException {
        if (fileData == null) {
            return null;
        } else if (fileData.getDataType() == FileData.FileDataType.zipEntry) {
            Path dataPath = Paths.get(fileData.getFileName());
            if (Files.isDirectory(dataPath)) {
                return openFileStream(dataPath.resolve(fileData.getEntryName()));
            } else if (Files.isRegularFile(dataPath)) {
                return openZipEntryStream(dataPath, fileData.getEntryName());
            } else {
                return null;
            }
        } else {
            Path dataPath = Paths.get(fileData.getFileName());
            if (Files.isDirectory(dataPath)) {
                return openFileStream(dataPath.resolve(fileData.getEntryName()));
            } else if (Files.isRegularFile(dataPath)) {
                return openFileStream(dataPath);
            } else {
                return null;
            }
        }
    }

    private static InputStream openFileStream(Path fp) throws IOException {
        return Files.newInputStream(fp);
    }

    private static InputStream openZipEntryStream(Path zipFilePath, String entryName) throws IOException {
        try (ZipFile archiveFile = new ZipFile(zipFilePath.toFile())) {
            ZipEntry ze = archiveFile.getEntry(entryName);
            if (ze != null) {
                return archiveFile.getInputStream(ze);
            } else {
                LOG.warn("Full {} archive scan for {}", zipFilePath, entryName);
                String imageFn = Paths.get(entryName).getFileName().toString();
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
    }

    private static InputStream getEntryStream(ZipFile archiveFile, ZipEntry zipEntry) {
        try {
            return archiveFile.getInputStream(zipEntry);
        } catch (IOException e) {
            return null;
        }
    }

}
