package org.janelia.colormipsearch.cmd.dataexport;

import java.util.Map;

import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;

public class ImageStoreMapping {
    private final String defaultImageStore;
    private final Map<ImageStoreKey, String> imageStoreByMetadataFields;

    public ImageStoreMapping(String defaultImageStore, Map<ImageStoreKey, String> imageStoreByMetadataFields) {
        this.defaultImageStore = defaultImageStore;
        this.imageStoreByMetadataFields = imageStoreByMetadataFields;
    }

    String getImageStore(AbstractNeuronMetadata neuronMetadata) {
        // key check a match by alignmentSpace and libraryName
        ImageStoreKey asWithLibraryKey = new ImageStoreKey(neuronMetadata.getAlignmentSpace(), neuronMetadata.getLibraryName());
        // key to check a match by alignmentSpace
        ImageStoreKey asKey = new ImageStoreKey(neuronMetadata.getAlignmentSpace());
        return imageStoreByMetadataFields.getOrDefault(
                asWithLibraryKey,
                imageStoreByMetadataFields.getOrDefault(asKey, defaultImageStore)
        );
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("null -> ").append(defaultImageStore).append(',');
        if (imageStoreByMetadataFields != null) {
            imageStoreByMetadataFields.forEach((k, v) ->
                    builder.append(k).append("  -> ").append(v).append(','));
        }
        return builder.toString();
    }
}
