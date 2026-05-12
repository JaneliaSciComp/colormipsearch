package org.janelia.colormipsearch.cmd.jacsdata;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JacsDataGetter {
    private static final Logger LOG = LoggerFactory.getLogger(JacsDataGetter.class);

    private final String dataServiceURL;
    private final String configURL;
    private final String authorization;
    private final int readBatchSize;

    public JacsDataGetter(String dataServiceURL,
                          String configURL,
                          String authorization,
                          int readBatchSize) {
        this.dataServiceURL = dataServiceURL;
        this.configURL = configURL;
        this.authorization = authorization;
        this.readBatchSize = readBatchSize;
    }

    Map<String, CDMIPSample> retrieveLMSamplesByName(Set<String> sampleNames) {
        return CDMIPSample.indexBySampleName(
                httpRetrieveLMSamplesByName(HttpHelper.createClient(), sampleNames));
    }

    private List<CDMIPSample> httpRetrieveLMSamplesByName(Client httpClient, Set<String> sampleNames) {
        if (CollectionUtils.isEmpty(sampleNames)) {
            return Collections.emptyList();
        } else {
            LOG.debug("Read LM metadata for {} samples", sampleNames.size());
            return HttpHelper.retrieveDataStreamForFieldValues(() -> httpClient.target(dataServiceURL)
                                    .path("/data/samples"),
                            authorization,
                            readBatchSize,
                            "name",
                            sampleNames,
                            new TypeReference<List<CDMIPSample>>() {
                            })
                    .collect(Collectors.toList());
        }
    }

    private List<CDMIPSample> httpRetrieveLMSamplesByRefs(Client httpClient, Set<String> sampleRefs) {
        LOG.debug("Read LM metadata for {} samples", sampleRefs.size());
        if (CollectionUtils.isEmpty(sampleRefs)) {
            return Collections.emptyList();
        }
        return HttpHelper.retrieveDataStreamForFieldValues(
                () -> httpClient.target(dataServiceURL).path("/data/samples"),
                authorization,
                readBatchSize,
                "refs",
                sampleRefs,
                new TypeReference<List<CDMIPSample>>() {})
                .collect(Collectors.toList());
    }



    private List<CDMIPBody> httpRetrieveEMNeuronsByDatasetAndBodyIds(Client httpClient,
                                                                     String emDataset,
                                                                     String emDatasetVersion,
                                                                     Set<String> neuronBodyIds) {
        if (CollectionUtils.isEmpty(neuronBodyIds)) {
            return Collections.emptyList();
        } else {
            LOG.debug("Read EM metadata for {} neurons", neuronBodyIds.size());
            return HttpHelper.retrieveDataStreamForFieldValues(() -> httpClient.target(dataServiceURL)
                            .path("/emdata/dataset")
                            .path(emDataset)
                            .path(emDatasetVersion),
                    authorization,
                    readBatchSize,
                    "name",
                    neuronBodyIds,
                    new TypeReference<List<CDMIPBody>>() {
                    }).collect(Collectors.toList());
        }
    }

    private List<CDMIPBody> httpRetrieveEMBodiesByRefs(Client httpClient, Set<String> emBodyRefs) {
        if (CollectionUtils.isEmpty(emBodyRefs)) {
            return Collections.emptyList();
        } else {
            LOG.debug("Read EM metadata for {} EM bodies", emBodyRefs.size());
            return HttpHelper.retrieveDataStreamForFieldValues(() -> httpClient.target(dataServiceURL)
                            .path("/emdata/emBodies"),
                    authorization,
                    readBatchSize,
                    "refs",
                    emBodyRefs,
                    new TypeReference<List<CDMIPBody>>() {
                    }).collect(Collectors.toList());
        }
    }

    /**
     * Retrieve color depth mips indexed by MIP IDs. The retrieved MIPs should have the corresponding
     * LM Sample or EM Body set
     *
     * @param mipIds MIP IDs that need to be retrieved from JACS
     * @return the retrieved Color Depth MIPs indexed by MIP ID
     */
    Map<String, ColorDepthMIP> retrieveCDMIPs(Set<String> mipIds) {
        if (CollectionUtils.isEmpty(mipIds)) {
            return Collections.emptyMap();
        } else {
            Client httpClient = HttpHelper.createClient();
            List<ColorDepthMIP> colorDepthMIPS = httpRetrieveCDMIPs(httpClient, mipIds);
            Set<String> lmSamplesToRetrieve = new HashSet<>();
            Set<String> emBodiesToRetrieve = new HashSet<>();
            colorDepthMIPS.forEach(cdmip -> {
                if (cdmip.needsEMBody()) {
                    emBodiesToRetrieve.add(cdmip.emBodyRef);
                } else if (cdmip.needsLMSample()) {
                    lmSamplesToRetrieve.add(cdmip.sampleRef);
                }
            });
            LOG.info("Retrieve {} LM samples", lmSamplesToRetrieve.size());
            Map<String, CDMIPSample> lmSamples = CDMIPSample.indexByRef(
                    httpRetrieveLMSamplesByRefs(httpClient, lmSamplesToRetrieve));
            LOG.info("Retrieve {} EM bodies", emBodiesToRetrieve.size());
            Map<String, CDMIPBody> emBodies = CDMIPBody.indexByRef(
                    httpRetrieveEMBodiesByRefs(httpClient, emBodiesToRetrieve));
            LOG.info("Update 3D stack info for {} mips", colorDepthMIPS.size());
            return colorDepthMIPS.stream()
                    .peek(cdmip -> {
                        if (cdmip.needsEMBody()) {
                            cdmip.emBody = emBodies.get(cdmip.emBodyRef);
                        } else if (cdmip.needsLMSample()) {
                            cdmip.sample = lmSamples.get(cdmip.sampleRef);
                        }
                    })
                    .collect(Collectors.toMap(n -> n.id, n -> n));
        }
    }

    private List<ColorDepthMIP> httpRetrieveCDMIPs(Client httpClient, Set<String> mipIds) {
        LOG.debug("Read {} MIPs", mipIds.size());
        return HttpHelper.retrieveDataStreamForFieldValues(
                        () -> httpClient.target(dataServiceURL).path("/data/colorDepthMIPsWithSamples"),
                        authorization,
                        readBatchSize,
                        "id",
                        mipIds,
                        new TypeReference<List<ColorDepthMIP>>() {})
                .collect(Collectors.toList());
    }

    Map<String, String> retrieveLibraryNameMapping() {
        Map<String, Object> configJSON = HttpHelper.retrieveData(
                HttpHelper.createClient().target(configURL).path("/cdm_library"),
                null, // this does not require any authorization
                new TypeReference<Map<String, Object>>() {},
                Collections.emptyMap());
        Object configEntry = configJSON.get("config");
        if (!(configEntry instanceof Map)) {
            LOG.error("Config entry from {} is null or it's not a map", configJSON);
            throw new IllegalStateException("Config entry not found");
        }
        Map<String, String> cdmLibraryNamesMapping = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> configEntryMap = (Map<String, Map<String, Object>>) configEntry;
        configEntryMap.forEach((lid, ldata) -> {
            String lname = (String) ldata.get("name");
            cdmLibraryNamesMapping.put(lid, lname);
        });
        LOG.info("Using {} for mapping library names", cdmLibraryNamesMapping);
        return cdmLibraryNamesMapping;
    }

}
