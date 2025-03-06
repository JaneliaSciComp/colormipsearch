package org.janelia.colormipsearch.cmd.cdsprocess;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.google.common.collect.Streams;

import org.janelia.colormipsearch.cds.PixelMatchScore;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cmd.CachedMIPsUtils;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Perform color depth mask search in the current process.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class LocalColorMIPSearchProcessor<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> extends AbstractColorMIPSearchProcessor<M, T> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalColorMIPSearchProcessor.class);
    private static final long _1M = 1024 * 1024;

    private final ExecutorService cdsExecutor;

    public LocalColorMIPSearchProcessor(Number cdsRunId,
                                        ColorMIPSearch colorMIPSearch,
                                        int localProcessingPartitionSize,
                                        ExecutorService cdsExecutor,
                                        Set<String> tags) {
        super(cdsRunId, colorMIPSearch, localProcessingPartitionSize, tags);
        this.cdsExecutor = cdsExecutor;
    }

    @Override
    public List<CDMatchEntity<M, T>> findAllColorDepthMatches(List<M> queryMIPs, List<T> targetMIPs) {
        long startTime = System.currentTimeMillis();
        int nQueries = queryMIPs.size();
        int nTargets = targetMIPs.size();

        LOG.info("Searching {} masks against {} targets", nQueries, nTargets);

        Scheduler scheduler = Schedulers.fromExecutorService(cdsExecutor);
        Flux<List<CDMatchEntity<M, T>>> allColorDepthSearches = Flux.fromIterable(queryMIPs)
                .index()
                .flatMap(indexedQueryMIP -> submitMaskSearches(indexedQueryMIP.getT1(), indexedQueryMIP.getT2(), targetMIPs, scheduler));
        LOG.info("Submitted all color depth searches for {} masks with {} targets in {}s - memory usage {}M",
                queryMIPs.size(), targetMIPs.size(),
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);

        List<CDMatchEntity<M, T>> allSearchResults = allColorDepthSearches
                .flatMap(Flux::fromIterable)
                .collectList()
                .block();

        LOG.info("Finished all color depth searches {} masks with {} targets in {}s - memory usage {}M",
                queryMIPs.size(), targetMIPs.size(), (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1);
        return allSearchResults;
    }

    private ParallelFlux<List<CDMatchEntity<M, T>>> submitMaskSearches(long mIndex, M queryMIP, List<T> targetMIPs, Scheduler scheduler) {
        NeuronMIP<M> queryImage = NeuronMIPUtils.loadComputeFile(queryMIP, ComputeFileType.InputColorDepthImage); // load image - no caching for the mask
        if (queryImage == null || queryImage.hasNoImageArray()) {
            LOG.error("No input color depth image found for mask {}", queryMIP);
            return Flux.<List<CDMatchEntity<M, T>>>empty().parallel();
        }
        ColorDepthSearchAlgorithm<PixelMatchScore> queryColorDepthSearch = colorMIPSearch.createQueryColorDepthSearchWithDefaultThreshold(queryImage.getImageArray());
        if (queryColorDepthSearch.getQuerySize() == 0) {
            LOG.info("No computation created for {} because it is empty", queryMIP);
            return Flux.<List<CDMatchEntity<M, T>>>empty().parallel();
        }
        ParallelFlux<List<CDMatchEntity<M, T>>> cdsComputations = Flux.fromIterable(targetMIPs)
                .buffer(localProcessingPartitionSize)
                .index()
                .parallel()
                .runOn(scheduler)
                .map(indexedTargetMIPsPartition -> {
                    LOG.debug("Compare mask# {} - {} with {} partition of {} items",
                            mIndex, queryMIP, indexedTargetMIPsPartition.getT1(), indexedTargetMIPsPartition.getT2().size());
                    long startTime = System.currentTimeMillis();
                    List<CDMatchEntity<M, T>> srs = indexedTargetMIPsPartition.getT2().stream()
                            .map(targetMIP -> CachedMIPsUtils.loadMIP(targetMIP, ComputeFileType.InputColorDepthImage))
                            .filter(NeuronMIPUtils::hasImageArray)
                            .map(targetImage -> findPixelMatch(queryColorDepthSearch, queryImage, targetImage))
                            .filter(m -> m.isMatchFound() && m.hasNoErrors())
                            .collect(Collectors.toList());
                    LOG.info("Found {} matches comparing mask# {} - {} with target partition {} of {} items in {}s",
                            srs.size(), mIndex, queryMIP, indexedTargetMIPsPartition.getT1(), indexedTargetMIPsPartition.getT2().size(),
                            (System.currentTimeMillis() - startTime) / 1000.);
                    return srs;
                });
        LOG.info("Submitted color depth searches for mask# {} - {} with {} targets",
                mIndex, queryMIP, targetMIPs.size());
        return cdsComputations;
    }

    @Override
    public void terminate() {
        // nothing to do here
    }
}
