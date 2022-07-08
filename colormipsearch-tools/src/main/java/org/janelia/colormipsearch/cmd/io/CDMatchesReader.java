package org.janelia.colormipsearch.cmd.io;

import java.util.List;

import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDSMatch;

public interface CDMatchesReader<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> {
    /**
     * This method will list the location for all color depth matches.
     *
     * A file based implementation will return all files that contain CD matches.
     *
     * @return
     */
    List<String> listCDMatchesLocations();
    List<CDSMatch<M, T>> readCDMatches(String maskSource);
}