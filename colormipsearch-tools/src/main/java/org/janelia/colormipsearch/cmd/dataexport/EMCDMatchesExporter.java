package org.janelia.colormipsearch.cmd.dataexport;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.janelia.colormipsearch.cmd.jacsdata.CachedDataHelper;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.CDMatchedTarget;
import org.janelia.colormipsearch.dto.EMNeuronMetadata;
import org.janelia.colormipsearch.dto.LMNeuronMetadata;
import org.janelia.colormipsearch.dto.ResultMatches;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.FileType;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchResultsGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EMCDMatchesExporter extends AbstractCDMatchesExporter {
    private static final Logger LOG = LoggerFactory.getLogger(EMCDMatchesExporter.class);

    public EMCDMatchesExporter(CachedDataHelper jacsDataHelper,
                               DataSourceParam dataSourceParam,
                               List<String> targetLibraries,
                               List<String> targetTags,
                               List<String> targetExcludedTags,
                               List<String> targetAnnotations,
                               List<String> targetExcludedAnnotations,
                               List<String> matchesExcludedTags,
                               ScoresFilter scoresFilter,
                               URLTransformer urlTransformer,
                               ImageStoreMapping imageStoreMapping,
                               Path outputDir,
                               Executor executor,
                               NeuronMatchesReader<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader,
                               NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao,
                               ItemsWriterToJSONFile resultMatchesWriter,
                               int processingPartitionSize) {
        super(jacsDataHelper,
                dataSourceParam,
                targetLibraries,
                targetTags,
                targetExcludedTags,
                targetAnnotations,
                targetExcludedAnnotations,
                matchesExcludedTags,
                scoresFilter,
                urlTransformer,
                imageStoreMapping,
                outputDir,
                executor,
                neuronMatchesReader,
                neuronMetadataDao,
                resultMatchesWriter,
                processingPartitionSize);
    }

    @Override
    public void runExport() {
        long startProcessingTime = System.currentTimeMillis();
        Collection<String> masks = neuronMatchesReader.listMatchesLocations(Collections.singletonList(dataSourceParam));
        List<CompletableFuture<Void>> allExportsJobs = ItemsHandling.partitionCollection(masks, processingPartitionSize)
                .entrySet().stream()
                .map(indexedPartition -> CompletableFuture.<Void>supplyAsync(() -> {
                    runExportForMaskIds(indexedPartition.getKey(), indexedPartition.getValue());
                    return null;
                }, executor))
                .collect(Collectors.toList());
        CompletableFuture.allOf(allExportsJobs.toArray(new CompletableFuture<?>[0])).join();
        LOG.info("Finished all exports in {}s", (System.currentTimeMillis()-startProcessingTime)/1000.);
    }

    private void runExportForMaskIds(int jobId, List<String> maskMipIds) {
        long startProcessingTime = System.currentTimeMillis();
        LOG.info("Start processing {} masks from partition {}", maskMipIds.size(), jobId);
        maskMipIds.forEach(maskMipId -> {
            LOG.info("Read EM color depth matches for {}", maskMipId);
            List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> allMatchesForMask = neuronMatchesReader.readMatchesByMask(
                    dataSourceParam.getAlignmentSpace(),
                    dataSourceParam.duplicate()
                            .resetLibraries()
                            .resetNames()
                            .resetMipIDs()
                            .addMipID(maskMipId)
                            .setOffset(0) // reset size and offset
                            .setSize(0),
                    new DataSourceParam()
                            .setAlignmentSpace(dataSourceParam.getAlignmentSpace())
                            .addLibraries(targetLibraries)
                            .addTags(targetTags)
                            .addExcludedTags(targetExcludedTags)
                            .addAnnotations(targetAnnotations)
                            .addExcludedAnnotations(targetExcludedAnnotations),
                    /* matchTags */null,
                    /* matchExcludedTags */matchesExcludedTags,
                    scoresFilter,
                    null/*no sorting because it uses too much memory on the server*/);
            List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> selectedMatchesForMask;
            LOG.info("Found {} color depth matches for mip {}", allMatchesForMask.size(), maskMipId);
            if (allMatchesForMask.isEmpty()) {
                // this occurs only when there really aren't any matches between the EM MIP and any of the LM MIPs
                PagedResult<AbstractNeuronEntity> neurons = neuronMetadataDao.findNeurons(new NeuronSelector().addMipID(maskMipId), new PagedRequest());
                if (neurons.isEmpty()) {
                    LOG.warn("No mask neuron found for {} - this should not have happened!", maskMipId);
                    return;
                }
                CDMatchEntity<AbstractNeuronEntity, ? extends AbstractNeuronEntity> fakeMatch = new CDMatchEntity<>();
                fakeMatch.setMaskImage(neurons.getResultList().get(0));
                selectedMatchesForMask = Collections.singletonList(fakeMatch);
            } else {
                LOG.info("Select best EM matches for {} out of {} matches", maskMipId, allMatchesForMask.size());
                selectedMatchesForMask = selectBestMatchPerMIPPair(allMatchesForMask);
            }
            LOG.info("Write {} color depth matches for {}", selectedMatchesForMask.size(), maskMipId);
            writeResults(selectedMatchesForMask);
        });
        LOG.info("Finished processing partition {} containig {} mips in {}s - memory usage {}M out of {}",
                jobId, maskMipIds.size(), (System.currentTimeMillis()-startProcessingTime)/1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        System.gc();
    }

    private <M extends EMNeuronMetadata, T extends LMNeuronMetadata> void
    writeResults(List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> matches) {
        // group results by mask MIP ID
        List<Function<M, ?>> grouping = Collections.singletonList(
                AbstractNeuronMetadata::getMipId
        );
        // order descending by normalized score
        Comparator<CDMatchedTarget<T>> ordering = Comparator.comparingDouble(m -> -m.getNormalizedScore());
        List<ResultMatches<M, CDMatchedTarget<T>>> groupedMatches = MatchResultsGrouping.groupByMask(
                matches,
                grouping,
                m -> m.getTargetImage() != null,
                ordering);
        // retrieve source ColorDepth MIPs
        retrieveAllCDMIPs(matches);
        Map<Number, NeuronPublishedURLs> indexedNeuronURLs = dataHelper.retrievePublishedURLs(
                matches.stream()
                        .flatMap(m -> Stream.of(m.getMaskImage(), m.getMatchedImage()))
                        .filter(Objects::nonNull) // this is possible for fake matches
                        .collect(Collectors.toSet())
        );
        LOG.info("Fill in missing info for {} matches", matches.size());
        // update all neuron from all grouped matches
        List<ResultMatches<M, CDMatchedTarget<T>>> publishedMatches = groupedMatches.stream()
                .peek(m -> updateMatchedResultsMetadata(m,
                        this::updateEMNeuron,
                        this::updateLMNeuron,
                        indexedNeuronURLs
                ))
                .filter(resultMatches -> resultMatches.getKey().isPublished()) // filter out unpublished EMs
                .peek(resultMatches -> resultMatches.setItems(resultMatches.getItems().stream()
                        // filter out unpublished LMs
                        .filter(m -> m.getTargetImage().isPublished())
                        // filter out matches that do not have uploaded files
                        .filter(m -> m.hasMatchFile(FileType.CDMInput) && m.hasMatchFile(FileType.CDMMatch))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
        // write results by mask MIP ID
        resultMatchesWriter.writeGroupedItemsList(publishedMatches, AbstractNeuronMetadata::getMipId, outputDir);
    }
}
