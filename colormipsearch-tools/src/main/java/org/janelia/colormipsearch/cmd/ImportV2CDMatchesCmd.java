package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.cmd.v2dataimport.JSONV2Em2LmMatchesReader;
import org.janelia.colormipsearch.dataio.CDMIPsReader;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCDMIPsReader;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.datarequests.SortDirection;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This command is used to export data from the database to the file system in order to upload it to S3.
 */
public class ImportV2CDMatchesCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ImportV2CDMatchesCmd.class);
    // the reverse library name mapping is hard-coded because it's a one time operation anyway
    // and I don't see any reason to introduce the config server.
    private static final Map<String, String> REVERSE_V2_LIBRARY_MAPPING = new LinkedHashMap<String, String>() {{
        put("FlyEM_Hemibrain_v1.2.1", "flyem_hemibrain_1_2_1");
        put("FlyLight Split-GAL4 Drivers", "flylight_split_gal4_published");
        put("FlyLight Gen1 MCFO", "flylight_gen1_mcfo_published");
        put("FlyLight Annotator Gen1 MCFO", "flylight_annotator_gen1_mcfo_published");
        put("FlyEM_VNC_v0.6", "flyem_vnc_0_6");
        put("FlyEM_VNC_v0.5", "flyem_vnc_0_5");
    }};

    @Parameters(commandDescription = "Import v2 color depth matches")
    static class ImportCDMatchesCmdArgs extends AbstractCmdArgs {

        @Parameter(names = {"--results", "-r"}, required = true, variableArity = true, converter = ListArg.ListArgConverter.class,
                description = "The location of the v2 results. This can be a list of directories or files ")
        List<ListArg> cdMatches = new ArrayList<>();

        @Parameter(names = {"--tag"}, required = true, description = "Tag to assign to the imported matches")
        String tag;

        @Parameter(names = {"--imported-neuron-tag"}, description = "Tag assigned to neurons created by this import process")
        String importedNeuronTag = "Created by import";

        @Parameter(names = {"--suspicious-match-tag"}, description = "Tag assigned to suspicious matches - these are matches that do not have a valid neuron reference")
        String suspiciousMatchTag = "Suspicious match";

        @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
        int processingPartitionSize = 100;

        public ImportCDMatchesCmdArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

    }

    private final ImportCDMatchesCmdArgs args;
    private final ObjectMapper mapper;

    public ImportV2CDMatchesCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new ImportCDMatchesCmdArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    public ImportCDMatchesCmdArgs getArgs() {
        return args;
    }

    @Override
    public void execute() {
        long startTime = System.currentTimeMillis();

        CDMIPsReader mipsReader = getCDMIPsReader();
        CDMIPsWriter mipsWriter = getCDMIPsWriter();
        NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader = getCDMatchesReader();
        NeuronMatchesWriter<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesWriter = getCDSMatchesWriter();

        // GET JSON files
        Collection<String> cdMatchesLocations = cdMatchesReader.listMatchesLocations(args.cdMatches.stream()
                .map(larg -> new DataSourceParam()
                        .addLibrary(larg.input)
                        .setOffset(larg.offset)
                        .setSize(larg.length))
                .collect(Collectors.toSet()));
        int size = cdMatchesLocations.size();
        // process JSON files
        ItemsHandling.partitionCollection(cdMatchesLocations, args.processingPartitionSize).entrySet().stream().parallel()
                .forEach(indexedPartititionItems -> {
                    long startProcessingPartitionTime = System.currentTimeMillis();
                    // process each item from the current partition sequentially
                    indexedPartititionItems.getValue().forEach(maskIdToProcess -> processCDMatchesFromFile(
                            maskIdToProcess, mipsReader, mipsWriter, cdMatchesReader, cdMatchesWriter));
                    LOG.info("Finished batch {} of {} in {}s - memory usage {}M out of {}M",
                            indexedPartititionItems.getKey(),
                            indexedPartititionItems.getValue().size(),
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                });
        LOG.info("Finished importing {} items in {}s - memory usage {}M out of {}M",
                size,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private void processCDMatchesFromFile(String cdMatchesFile,
                                          CDMIPsReader mipsReader,
                                          CDMIPsWriter mipsWriter,
                                          NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader,
                                          NeuronMatchesWriter<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesWriter) {
        try {
            // read all matches for the current mask
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesForMask = getCDMatchesForMask(cdMatchesReader, cdMatchesFile);
            LOG.info("Read {} items from {}", cdMatchesForMask.size(), cdMatchesFile);
            cdMatchesForMask.forEach(m -> {
                m.getMaskImage().setLibraryName(getLibraryName(m.getMaskImage().getLibraryName()));
                m.getMatchedImage().setLibraryName(getLibraryName(m.getMatchedImage().getLibraryName()));
                m.addTag(args.tag);
            });
            // update MIP IDs for all masks
            updateMIPRefs(cdMatchesForMask, AbstractMatchEntity::getMaskImage, mipsReader, cdMatchesFile);
            // update MIP IDs for all targets
            updateMIPRefs(cdMatchesForMask, AbstractMatchEntity::getMatchedImage, mipsReader, cdMatchesFile);
            // write matches
            cdMatchesWriter.write(cdMatchesForMask);
            // update processing tags
            mipsWriter.addProcessingTags(
                    cdMatchesForMask.stream()
                            .flatMap(cdm -> Stream.of(cdm.getMaskImage(), cdm.getMatchedImage()))
                            .filter(Objects::nonNull)
                            .filter(n -> n.hasProcessedTag(ProcessingType.ColorDepthSearch, args.tag))
                            .collect(Collectors.toList()),
                    ProcessingType.ColorDepthSearch,
                    Collections.singleton(args.tag)
            );

        } catch (Exception e) {
            throw new IllegalArgumentException("Error processing " + cdMatchesFile, e);
        }
    }

    private CDMIPsReader getCDMIPsReader() {
        return new DBCDMIPsReader(getDaosProvider().getNeuronMetadataDao());
    }

    private CDMIPsWriter getCDMIPsWriter() {
        return new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao());
    }

    private NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> getCDMatchesReader() {
        return new JSONV2Em2LmMatchesReader(mapper);
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesWriter<CDMatchEntity<M, T>>
    getCDSMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            // always create new matches
            return new DBNeuronMatchesWriter<>(getDaosProvider().getCDMatchesDao());
        } else {
            throw new IllegalArgumentException("This class should only be used for importing intermediated results into the database");
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMask(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, String maskCDMipId) {
        LOG.info("Read all color depth matches for {}", maskCDMipId);
        // it's not clear from the API but the reader is file based so tags are not important here
        return cdsMatchesReader.readMatchesByMask(
                null,
                new DataSourceParam()
                        .addMipID(maskCDMipId),
                new DataSourceParam(),
                /* matchTags */null,
                /* matchExcludedTags */null,
                /* matchScoresFilter */null,
                Collections.singletonList(
                        new SortCriteria("normalizedScore", SortDirection.DESC)
                ),
                /* readPageSize */-1);
    }

    private String getLibraryName(String lname) {
        return REVERSE_V2_LIBRARY_MAPPING.getOrDefault(lname, lname);
    }

    private void updateMIPRefs(List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> matches,
                               Function<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>, AbstractNeuronEntity> mipSelector,
                               CDMIPsReader cdmiPsReader,
                               String filename) {
        CDMIPsWriter cdMIPsWriter = new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao());
        Map<AbstractNeuronEntity, AbstractNeuronEntity> indexedPersistedMIPs = matches.stream()
                .map(mipSelector)
                .collect(
                        Collectors.groupingBy(
                                m -> ImmutablePair.of(m.getAlignmentSpace(), m.getLibraryName()),
                                Collectors.toSet()))
                .entrySet().stream()
                .flatMap(e -> cdmiPsReader.readMIPs(
                        new DataSourceParam()
                                .setAlignmentSpace(e.getKey().getLeft())
                                .addLibrary(e.getKey().getRight())
                                .addMipIDs(e.getValue().stream().map(AbstractNeuronEntity::getMipId).collect(Collectors.toSet()))).stream())
                .collect(Collectors.toMap(n -> {
                            AbstractNeuronEntity n1 = n.duplicate();
                            // just to make sure - reset the entity ID because we don't want the key to match based on entity ID
                            n1.setEntityId(null);
                            return n1;
                        },
                        n -> n,
                        (n1, n2) -> {
                            // resolve the conflict by selecting the first element
                            LOG.warn("Conflict found for {}, {} while processing {}", n1, n2, filename);
                            return n1;
                        }));
        Map<AbstractNeuronEntity, AbstractNeuronEntity> newNeurons = new HashMap<>();
        // update the entity IDs
        matches.forEach(cdm -> {
            AbstractNeuronEntity n = mipSelector.apply(cdm);
            // check if the neuron has been persisted either when MIPs were imported or as part of the current import process
            AbstractNeuronEntity persistedNeuron = indexedPersistedMIPs.getOrDefault(n, newNeurons.get(n));
            if (persistedNeuron != null) {
                n.setEntityId(persistedNeuron.getEntityId());
                n.addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton(args.tag));
                if (persistedNeuron.hasTag(args.importedNeuronTag)) {
                    cdm.addTag(args.suspiciousMatchTag);
                }
            } else {
                /*
                 * I don't know if this is a good idea or not because it leads to importing actually bad matches.
                 */
                /*
                 * create the key as a duplicate of the current neuron;
                 * we don't use the same object because the save method will assign an entity ID so
                 * neurons without entity IDs will no longer match
                */
                AbstractNeuronEntity nKey = n.duplicate();
                LOG.info("No persisted MIP found for {}({}) in color depth match {}",
                        n, n.getComputeFileData(ComputeFileType.InputColorDepthImage), cdm);
                // persist the neuron now and assign it a specific tag
                n.addTag(args.importedNeuronTag);
                n.addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton(args.tag));
                cdMIPsWriter.writeOne(n);
                newNeurons.put(nKey, n);
                cdm.addTag(args.suspiciousMatchTag);
            }
        });
    }

}
