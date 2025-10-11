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
import org.janelia.colormipsearch.model.ProcessingType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchResultsGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LMCDMatchesExporter extends AbstractCDMatchesExporter {
    private static final Logger LOG = LoggerFactory.getLogger(LMCDMatchesExporter.class);

    public LMCDMatchesExporter(CachedDataHelper jacsDataHelper,
                               DataSourceParam dataSourceParam,
                               List<String> maskLibraries,
                               List<String> maskTags,
                               List<String> maskExcludedTags,
                               List<String> maskAnnotations,
                               List<String> maskExcludedAnnotations,
                               List<String> matchesExcludedTags,
                               ScoresFilter scoresFilter,
                               URLTransformer urlTransformer,
                               ImageStoreMapping imageStoreMapping,
                               Path outputDir,
                               Executor executor,
                               NeuronMatchesReader<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader,
                               NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao,
                               ItemsWriterToJSONFile resultMatchesWriter,
                               int processingPartitionSize,
                               int maxMatchedTargets,
                               int maxMatchesWithSameNamePerMIP,
                               int readPageSize) {
        super(jacsDataHelper,
                dataSourceParam,
                maskLibraries,
                maskTags,
                maskExcludedTags,
                maskAnnotations,
                maskExcludedAnnotations,
                matchesExcludedTags,
                scoresFilter,
                urlTransformer,
                imageStoreMapping,
                outputDir,
                executor,
                neuronMatchesReader,
                neuronMetadataDao,
                resultMatchesWriter,
                processingPartitionSize,
                maxMatchedTargets,
                maxMatchesWithSameNamePerMIP,
                readPageSize);
    }

    @Override
    public void runExport() {
        long startProcessingTime = System.currentTimeMillis();
        Collection<String> targets = neuronMatchesReader.listMatchesLocations(Collections.singletonList(dataSourceParam));
        List<CompletableFuture<Void>> allExportsJobs = ItemsHandling.partitionCollection(targets, processingPartitionSize)
                .entrySet().stream()
                .map(indexedPartition -> CompletableFuture.<Void>supplyAsync(() -> {
                    runExportForTargetIds(indexedPartition.getKey(), indexedPartition.getValue());
                    LOG.info("Completed partition {}", indexedPartition.getKey());
                    return null;
                }, executor))
                .collect(Collectors.toList());
        CompletableFuture.allOf(allExportsJobs.toArray(new CompletableFuture<?>[0])).join();
        LOG.info("Finished all exports in {}s", (System.currentTimeMillis() - startProcessingTime) / 1000.);
    }

    private void runExportForTargetIds(int jobId, List<String> targetMipIds) {
        long startProcessingTime = System.currentTimeMillis();
        LOG.info("Start processing {} targets from partition {}", targetMipIds.size(), jobId);
        targetMipIds.forEach(targetMipId -> {
            LOG.info("Read LM color depth matches for mip {} (pageSize={})", targetMipId, readPageSize);
            List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> allMatchesForTarget = neuronMatchesReader.readMatchesByTarget(
                    dataSourceParam.getAlignmentSpace(),
                    new DataSourceParam()
                            .setAlignmentSpace(dataSourceParam.getAlignmentSpace())
                            .addLibraries(targetLibraries)
                            .addTags(targetTags)
                            .addExcludedTags(targetExcludedTags)
                            .addAnnotations(targetAnnotations)
                            .addExcludedAnnotations(targetExcludedAnnotations),
                    dataSourceParam.duplicate()
                            .resetLibraries()
                            .resetNames()
                            .resetMipIDs()
                            .addMipID(targetMipId),
                    /* matchTags */null,
                    /* matchExcludedTags */matchesExcludedTags,
                    /* matchesScoresFilter */scoresFilter,
                    /* no sorting yet because it uses too much memory on the server */null,
                    /* from */0,
                    /* nRecords */-1,
                    /* readPageSize */readPageSize);
            LOG.info("Found {} color depth matches for mip {}", allMatchesForTarget.size(), targetMipId);
            List<CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> selectedMatchesForTarget;
            if (allMatchesForTarget.isEmpty()) {
                // this can happen even when there are EM - LM matches but the match is low ranked and it has no gradient score
                // therefore no LM - EM match is found
                // in this case we need to retrieve the LM MIP info and create an empty result set
                PagedResult<AbstractNeuronEntity> neurons = neuronMetadataDao.findNeurons(new NeuronSelector().addMipID(targetMipId), new PagedRequest());
                if (neurons.isEmpty()) {
                    LOG.warn("No target neuron found for {} - this should not have happened!", targetMipId);
                    return;
                }
                CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity> fakeMatch = neurons.getResultList().stream()
                        .filter(n -> n.hasAnyProcessedTag(ProcessingType.ColorDepthSearch) || n.hasAnyProcessedTag(ProcessingType.PPPMatch))
                        .findAny()
                        .map(n -> {
                            CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity> m = new CDMatchEntity<>();
                            m.setMatchedImage(n);
                            return m;
                        })
                        .orElse(null);
                if (fakeMatch == null) {
                    LOG.warn("No processing found for mip {}, so no result file will be generated", targetMipId);
                    return;
                }
                selectedMatchesForTarget = Collections.singletonList(fakeMatch);
            } else {
                LOG.info("Select best LM matches for {} out of {} matches", targetMipId, allMatchesForTarget.size());
                selectedMatchesForTarget = selectBestMatchPerMIPPair(allMatchesForTarget);
            }
            LOG.info("Write {} color depth matches for {}", selectedMatchesForTarget.size(), targetMipId);
            writeResults(selectedMatchesForTarget);
        });
        LOG.info("Finished processing partition {} containing {} mips in {}s - memory usage {}M out of {}M",
                jobId, targetMipIds.size(), (System.currentTimeMillis() - startProcessingTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
        System.gc();
    }

    private <M extends EMNeuronMetadata, T extends LMNeuronMetadata> void
    writeResults(List<CDMatchEntity<AbstractNeuronEntity, AbstractNeuronEntity>> matches) {
        // group results by target MIP ID
        List<Function<T, ?>> grouping = Collections.singletonList(
                AbstractNeuronMetadata::getMipId
        );
        // order descending by normalized score
        Comparator<CDMatchedTarget<M>> ordering = Comparator.comparingDouble(m -> -m.getNormalizedScore());
        List<ResultMatches<T, CDMatchedTarget<M>>> groupedMatches = MatchResultsGrouping.groupByTarget(
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
        List<ResultMatches<T, CDMatchedTarget<M>>> publishedMatches = groupedMatches.stream()
                .peek(m -> updateMatchedResultsMetadata(m,
                        this::updateLMNeuron,
                        this::updateEMNeuron,
                        indexedNeuronURLs
                ))
                .filter(resultMatches -> {
                    if (resultMatches.getKey().isUnpublished()) {
                        LOG.warn("Ignore matches for neuron {} - it was unpublished because: {}",
                                resultMatches.getKey(), resultMatches.getKey().getUnpublishReasons());
                        return false;
                    }
                    return true;
                }) // filter out unpublished LMs
                .peek(resultMatches -> resultMatches.setItems(resultMatches.getItems().stream()
                        // filter out unpublished EMs
                        .filter(m -> {
                            if (m.getTargetImage().isUnpublished()) {
                                LOG.warn("Removed match {} for {} - {} was unpublished because: {}", m, resultMatches.getKey(),
                                        m.getTargetImage(), m.getTargetImage().getUnpublishReasons());
                                return false;
                            }
                            return true;
                        })
                        // filter out matches that do not have uploaded files
                        .filter(m -> m.hasMatchFile(FileType.CDMInput) && m.hasMatchFile(FileType.CDMMatch))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
        // write results by target MIP ID
        resultMatchesWriter.writeGroupedItemsList(publishedMatches, AbstractNeuronMetadata::getMipId, outputDir);
    }
}
