package org.janelia.colormipsearch.cmd.jacsdata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.janelia.colormipsearch.model.PublishedLMImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedDataHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CachedDataHelper.class);
    /**
     * Both color depth mips cache and LM sample "cache" must be thread safe as they
     * can be updated from multiple threads.
     */
    private static final Map<String, ColorDepthMIP> CD_MIPS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CDMIPSample> LM_SAMPLES_CACHE = new ConcurrentHashMap<>();

    private final JacsDataGetter jacsDataGetter;
    private final PublishedDataGetter publishedDataGetter;
    private Map<String, String> libraryNameMapping;

    public CachedDataHelper(JacsDataGetter jacsDataGetter,
                            PublishedDataGetter publishedDataGetter) {
        this.jacsDataGetter = jacsDataGetter;
        this.publishedDataGetter = publishedDataGetter;
    }

    public void cacheCDMIPs(Set<String> mipIds) {
        if (CollectionUtils.isNotEmpty(mipIds)) {
            Set<String> missingMipIDs = mipIds.stream().filter(mipId -> !CD_MIPS_CACHE.containsKey(mipId)).collect(Collectors.toSet());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrieve {} MIPs to populate missing information", missingMipIDs);
            } else {
                LOG.info("Retrieve {} MIPs to populate missing information", missingMipIDs.size());
            }
            Map<String, ColorDepthMIP> missingCDMIPs = jacsDataGetter.retrieveCDMIPs(missingMipIDs);
            publishedDataGetter.update3DStacksForAllMips(missingCDMIPs.values());
            CD_MIPS_CACHE.putAll(missingCDMIPs);
        }
    }

    public ColorDepthMIP getColorDepthMIP(String mipId) {
        return CD_MIPS_CACHE.get(mipId);
    }

    public String getLibraryName(String libname) {
        if (libraryNameMapping == null) {
            libraryNameMapping = jacsDataGetter.retrieveLibraryNameMapping();
        }
        return libraryNameMapping.getOrDefault(libname, libname);
    }

    public Map<String, CDMIPSample> retrieveLMSamplesByName(Set<String> lmSampleNames) {
        if (CollectionUtils.isNotEmpty(lmSampleNames)) {
            Set<String> toRetrieve = lmSampleNames.stream().filter(n -> !LM_SAMPLES_CACHE.containsKey(n)).collect(Collectors.toSet());
            LOG.info("Retrieve {} samples to populate missing information", toRetrieve.size());
            Map<String, CDMIPSample> retrievedSamples = jacsDataGetter.retrieveLMSamplesByName(toRetrieve);
            LM_SAMPLES_CACHE.putAll(retrievedSamples);
            return LM_SAMPLES_CACHE.entrySet().stream()
                    .filter(e -> lmSampleNames.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            return Collections.emptyMap();
        }
    }

    public Map<String, List<PublishedLMImage>> retrievePublishedImages(String alignmentSpace, Set<String> sampleRefs) {
        return publishedDataGetter.retrievePublishedImages(alignmentSpace, sampleRefs);
    }

    public Map<Number, NeuronPublishedURLs> retrievePublishedURLs(Collection<AbstractNeuronEntity> neurons) {
        return publishedDataGetter.retrievePublishedURLs(neurons.stream().map(AbstractBaseEntity::getEntityId).collect(Collectors.toSet()));
    }

    public CDMIPSample getLMSample(String lmName) {
        return LM_SAMPLES_CACHE.get(lmName);
    }

}
