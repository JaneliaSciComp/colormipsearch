package org.janelia.colormipsearch.cmd.dataexport;

import java.nio.file.Path;
import java.util.concurrent.Executor;

import org.janelia.colormipsearch.cmd.jacsdata.CachedDataHelper;
import org.janelia.colormipsearch.cmd.jacsdata.ColorDepthMIP;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.EMNeuronMetadata;
import org.janelia.colormipsearch.dto.LMNeuronMetadata;
import org.janelia.colormipsearch.model.FileType;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDataExporter implements DataExporter {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataExporter.class);

    final CachedDataHelper dataHelper;
    final DataSourceParam dataSourceParam;
    private final URLTransformer urlTransformer;
    private final ImageStoreMapping imageStoreMapping;
    final Path outputDir;
    final Executor executor;

    protected AbstractDataExporter(CachedDataHelper dataHelper,
                                   DataSourceParam dataSourceParam,
                                   URLTransformer urlTransformer,
                                   ImageStoreMapping imageStoreMapping,
                                   Path outputDir,
                                   Executor executor) {
        this.dataHelper = dataHelper;
        this.dataSourceParam = dataSourceParam;
        this.urlTransformer = urlTransformer;
        this.imageStoreMapping = imageStoreMapping;
        this.outputDir = outputDir;
        this.executor = executor;
    }

    @Override
    public DataSourceParam getDataSource() {
        return dataSourceParam;
    }

    void updateEMNeuron(EMNeuronMetadata emNeuron, NeuronPublishedURLs neuronPublishedURLs) {
        LOG.debug("Update EM neuron for {} - mipID: {}", emNeuron.getInternalId(), emNeuron.getMipId());
        ColorDepthMIP mip = dataHelper.getColorDepthMIP(emNeuron.getMipId());
        if (mip == null) {
            emNeuron.unpublish("no MIP found");
            return;
        }
        // the order matter here because the mapping should be defined on the internal library name
        // so imageStore must be set before the library name was changed
        updateFileStore(emNeuron);
        emNeuron.setLibraryName(dataHelper.getLibraryName(emNeuron.getLibraryName()));
        mip.updateEMNeuron(emNeuron, neuronPublishedURLs);
    }

    void updateLMNeuron(LMNeuronMetadata lmNeuron, NeuronPublishedURLs neuronPublishedURLs) {
        LOG.debug("Update LM neuron for {} - mipID: {}", lmNeuron.getInternalId(), lmNeuron.getMipId());
        ColorDepthMIP mip = dataHelper.getColorDepthMIP(lmNeuron.getMipId());
        if (mip == null) {
            lmNeuron.unpublish("no MIP found"); // if no mip was found set the neuron to unpublished
            return;
        }
        // the order matter here because the mapping should be defined on the internal library name
        // so imageStore must be set before the library name was changed
        updateFileStore(lmNeuron);
        lmNeuron.setLibraryName(dataHelper.getLibraryName(lmNeuron.getLibraryName()));
        mip.updateLMNeuron(lmNeuron, neuronPublishedURLs);
    }

    void updateFileStore(AbstractNeuronMetadata neuronMetadata) {
        neuronMetadata.setNeuronFile(FileType.store, imageStoreMapping.getImageStore(neuronMetadata));
    }

    String relativizeURL(FileType ft, String aUrl) {
        return urlTransformer.relativizeURL(ft, aUrl);
    }

}
