package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.janelia.colormipsearch.cds.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeuronMIPUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeuronMIPUtils.class);

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier<? extends IntegerType<?>>> getImageProviders(N neuron, Set<ComputeFileType> fileTypes, NeuronMIPLoader<N> neuronMIPLoader) {
        return fileTypes.stream()
                .filter(neuron::hasComputeFile)
                .map(cft -> new NeuronMIPVariantSupplier<>(neuron, cft, neuronMIPLoader))
                .collect(Collectors.toMap(
                        NeuronMIPVariantSupplier::getComputeFileType,
                        s -> s))
                ;
    }

    /**
     * Load a variant MIP when the neuron plays the role of a query in the matching process
     *
     * @param neuronMetadata
     * @param computeFileType
     * @return
     */
    @Nullable
    public static <N extends AbstractNeuronEntity> NeuronMIP<N> loadQueryVariant(@Nullable N neuronMetadata,
                                                                                 ComputeFileType computeFileType) {
        if (neuronMetadata == null) {
            return null;
        } else {
            LOG.debug("Load MIP {}:{}", neuronMetadata, computeFileType);
            FileData neuronFile = neuronMetadata.getComputeFileData(computeFileType);
            ImageLoader<? extends IntegerType<?>> imageLoader;
            if (neuronFile != null) {
                switch (computeFileType) {
                    case InputColorDepthImage:
                    case SourceColorDepthImage:
                    case ZGapImage:
                        imageLoader = new RGBImageLoader<>(neuronMetadata.getAlignmentSpace(), new ByteArrayRGBPixelType());
                        break;
                    case GradientImage:
                        imageLoader = new GrayImageLoader<>(neuronMetadata.getAlignmentSpace(), new UnsignedShortType());
                        break;
                    case SkeletonSWC:
                        imageLoader = new SWCImageLoader<>(neuronMetadata.getAlignmentSpace(), 0.5, 1, new UnsignedShortType(255));
                        break;
                    case Vol3DSegmentation:
                        imageLoader = new GrayImageLoader<>(neuronMetadata.getAlignmentSpace(), new UnsignedShortType());
                        break;
                    case SkeletonOBJ:
                    default:
                        throw new IllegalArgumentException("Unsupported file type " + computeFileType);
                }
                RandomAccessibleInterval<? extends IntegerType<?>> loadedImage = imageLoader.loadImage(neuronFile);
                return createNeuronMIP(neuronMetadata, computeFileType, loadedImage);
            } else {
                return new NeuronMIP<>(neuronMetadata, null, null);
            }
        }
    }

    /**
     * Load a variant MIP when the neuron plays the role of a target in the matching process
     *
     * @param neuronMetadata
     * @param computeFileType
     * @return
     */
    @Nullable
    public static <N extends AbstractNeuronEntity> NeuronMIP<N> loadTargetVariant(@Nullable N neuronMetadata,
                                                                                  ComputeFileType computeFileType) {
        if (neuronMetadata == null) {
            return null;
        } else {
            LOG.debug("Load MIP {}:{}", neuronMetadata, computeFileType);
            FileData neuronFile = neuronMetadata.getComputeFileData(computeFileType);
            ImageLoader<? extends IntegerType<?>> imageLoader;
            if (neuronFile != null) {
                switch (computeFileType) {
                    case InputColorDepthImage:
                    case SourceColorDepthImage:
                    case ZGapImage:
                        imageLoader = new RGBImageLoader<>(neuronMetadata.getAlignmentSpace(), new ByteArrayRGBPixelType());
                        break;
                    case GradientImage:
                        imageLoader = new GrayImageLoader<>(neuronMetadata.getAlignmentSpace(), new UnsignedShortType());
                        break;
                    case SkeletonSWC:
                        imageLoader = new SWCImageLoader<>(neuronMetadata.getAlignmentSpace(), 1.0, 20, new UnsignedShortType(255));
                        break;
                    case Vol3DSegmentation:
                        imageLoader = new GrayImageLoader<>(neuronMetadata.getAlignmentSpace(), new UnsignedShortType());
                        break;
                    case SkeletonOBJ:
                    default:
                        throw new IllegalArgumentException("Unsupported file type " + computeFileType);
                }
                RandomAccessibleInterval<? extends IntegerType<?>> loadedImage = imageLoader.loadImage(neuronFile);
                return createNeuronMIP(neuronMetadata, computeFileType, loadedImage);
            } else {
                return new NeuronMIP<>(neuronMetadata, null, null);
            }
        }
    }

    public static ImageLoader<? extends IntegerType<?>> getROIMaskLoader(String alignmentSpace) {
        return new GrayImageLoader<>(alignmentSpace, new ByteType());
    }

    @SuppressWarnings("unchecked")
    private static <N extends AbstractNeuronEntity> NeuronMIP<N> createNeuronMIP(N neuronMetadata,
                                                                                 ComputeFileType fileType,
                                                                                 RandomAccessibleInterval<? extends IntegerType<?>> img) {
        return new NeuronMIP<>(neuronMetadata, fileType, img);
    }

    public static boolean hasImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP != null && neuronMIP.hasImageArray();
    }

    public static boolean hasNoImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP == null || neuronMIP.hasNoImageArray();
    }

    public static RandomAccessibleInterval<? extends IntegerType<?>> getImageArray(@Nullable NeuronMIP<?> neuronMIP) {
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


}
