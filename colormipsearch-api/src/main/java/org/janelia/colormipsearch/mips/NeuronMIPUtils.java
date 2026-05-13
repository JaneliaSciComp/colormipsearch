package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeuronMIPUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeuronMIPUtils.class);

    public static <N extends AbstractNeuronEntity>
    Map<ComputeFileType, ComputeVariantImageSupplier> getImageSuppliers(N neuron, Set<ComputeFileType> fileTypes, NeuronMIPLoader<N> neuronMIPLoader) {
        return fileTypes.stream()
                .filter(neuron::hasComputeFile)
                .map(cft -> new NeuronMIPVariantSupplier<>(neuron, cft, neuronMIPLoader))
                .collect(Collectors.toMap(
                        NeuronMIPVariantSupplier::getComputeFileType,
                        s -> s))
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
            LOG.info("No neuron metadata provided to load {} MIP", computeFileType);
            return null;
        } else {
            LOG.trace("Load MIP {}:{}", neuronMetadata, computeFileType);
            FileData neuronFile = neuronMetadata.getComputeFileData(computeFileType);
            if (neuronFile != null) {
                LOG.trace("MIP array {}:{} loaded", neuronMetadata, computeFileType);
                ImageLoader imageLoader;
                switch (computeFileType) {
                    case SkeletonSWC:
                        imageLoader = new SWCImageLoader(neuronMetadata.getAlignmentSpace(), 1, 1);
                        break;
                    default:
                        imageLoader = new DefaultImageLoader(neuronMetadata.getAlignmentSpace());
                        break;
                }
                return new NeuronMIP<>(neuronMetadata, neuronFile, loadImageFromFileData(neuronFile, imageLoader));
            } else {
                LOG.info("No MIP {}:{} found", neuronMetadata, computeFileType);
                return new NeuronMIP<>(neuronMetadata, null, null);
            }
        }
    }

    public static ImageArray loadImageFromFileData(FileData fd, ImageLoader imageLoader) {
        long startTime = System.currentTimeMillis();
        InputStream inputStream;
        try {
            inputStream = FileDataUtils.openInputStream(fd);
            if (inputStream == null) {
                LOG.debug("No input stream for {}", fd);
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            LOG.trace("Load image array from {}", fd);
            return imageLoader.loadImage(fd.getName(), inputStream);
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

    public static ImageArray getImageArray(@Nullable NeuronMIP<?> neuronMIP) {
        return neuronMIP != null ? neuronMIP.getImageArray() : null;
    }

    public static <N extends AbstractNeuronEntity> N getMetadata(@Nullable NeuronMIP<N> neuronMIP) {
        return neuronMIP != null ? neuronMIP.getNeuronInfo() : null;
    }

}
