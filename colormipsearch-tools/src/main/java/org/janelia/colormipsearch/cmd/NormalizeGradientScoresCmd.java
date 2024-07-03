package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.cds.CombinedMatchScore;
import org.janelia.colormipsearch.cds.GradientAreaGapUtils;
import org.janelia.colormipsearch.cds.ShapeMatchScore;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.datarequests.SortDirection;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
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
 * Command to calculate the gradient scores.
 */
class NormalizeGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(NormalizeGradientScoresCmd.class);

    @Parameters(commandDescription = "Normalize gradient scores. The scores will be normalized with respect to the selected subset based on specified target filters")
    static class NormalizeGradientScoresArgs extends AbstractGradientScoresArgs {
        NormalizeGradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final NormalizeGradientScoresArgs args;
    private final ObjectMapper mapper;

    NormalizeGradientScoresCmd(String commandName,
                               CommonArgs commonArgs) {
        super(commandName);
        this.args = new NormalizeGradientScoresArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    NormalizeGradientScoresArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // run gradient scoring
        normalizeAllGradientScores();
    }

    private void normalizeAllGradientScores() {
        long startTime = System.currentTimeMillis();
        NeuronMatchesReader<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesReader = getCDMatchesReader();
        List<String> matchesMasksToProcess = cdMatchesReader.listMatchesLocations(
                args.masksLibraries.stream()
                        .map(larg -> new DataSourceParam()
                                .setAlignmentSpace(args.alignmentSpace)
                                .addLibrary(larg.input)
                                .addNames(args.masksPublishedNames)
                                .addMipIDs(args.masksMIPIDs)
                                .addDatasets(args.maskDatasets)
                                .addTags(args.maskTags)
                                .setOffset(larg.offset)
                                .setSize(larg.length))
                .collect(Collectors.toList()));
        int size = matchesMasksToProcess.size();
        Executor executor = CmdUtils.createCmdExecutor(args.commonArgs);
        // partition matches and process all partitions concurrently
        ItemsHandling.partitionCollection(matchesMasksToProcess, args.processingPartitionSize).entrySet().stream().parallel()
                .forEach(indexedPartition -> {
                    LOG.info("Start processing partition {} ({} items)",
                            indexedPartition.getKey(),
                            indexedPartition.getValue().size());
                    long startProcessingPartitionTime = System.currentTimeMillis();
                    // process each item from the current partition sequentially 
                    indexedPartition.getValue().forEach(maskIdToProcess -> {
                        // read all matches for the current mask
                        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesForMask = getCDMatchesForMask(cdMatchesReader, maskIdToProcess);
                        // normalize the grad scores
                        LOG.info("Normalize grad scores for {} matches of {}", cdMatchesForMask.size(), maskIdToProcess);
                        updateNormalizedScores(cdMatchesForMask);
                        // write the updated scores
                        long writtenUpdates = updateCDMatches(cdMatchesForMask);
                        LOG.info("Updated {} grad scores for {} matches of {}", writtenUpdates, cdMatchesForMask.size(), maskIdToProcess);
                        if (StringUtils.isNotBlank(args.processingTag)) {
                            long updatesWithProcessedTag = updateProcessingTag(cdMatchesForMask);
                            LOG.info("Set processing tag {} for {} mips", args.getProcessingTag(), updatesWithProcessedTag);
                        }
                    });
                    LOG.info("Finished partition {} ({} items) in {}s - memory usage {}M out of {}M",
                            indexedPartition.getKey(),
                            indexedPartition.getValue().size(),
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                });
        LOG.info("Finished normalizing gradient scores for {} items in {}s - memory usage {}M out of {}M",
                size,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesReader<CDMatchEntity<M, T>> getCDMatchesReader() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            DaosProvider daosProvider = getDaosProvider();
            return new DBNeuronMatchesReader<>(
                    daosProvider.getNeuronMetadataDao(),
                    daosProvider.getCDMatchesDao(),
                    "mipId");
        } else {
            return new JSONNeuronMatchesReader<>(mapper);
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> NeuronMatchesWriter<CDMatchEntity<M, T>> getCDMatchesWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return new DBNeuronMatchesWriter<>(getDaosProvider().getCDMatchesDao());
        } else {
            return new JSONNeuronMatchesWriter<>(
                    args.commonArgs.noPrettyPrint ? mapper.writer() : mapper.writerWithDefaultPrettyPrinter(),
                    AbstractNeuronEntity::getMipId, // group results by neuron MIP ID
                    Comparator.comparingDouble(m -> -(((CDMatchEntity<?,?>) m).getNormalizedScore())), // descending order by matching pixels
                    args.getOutputDir(),
                    null
            );
        }
    }

    private Optional<CDMIPsWriter> getCDMipsWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            return Optional.of(new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * The method calculates and updates normalized gradient scores for all color depth matches of the given mask MIP ID.
     *
     * @param cdMatches                  color depth matches for which the grad score will be computed
     * @param <M>                        mask type
     * @param <T>                        target type
     */
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void updateNormalizedScores(List<CDMatchEntity<M, T>> cdMatches) {
        Set<String> masksNames = cdMatches.stream()
                .map(cdm -> cdm.getMaskImage().getPublishedName())
                .collect(Collectors.toSet());
        // get max scores for normalization
        CombinedMatchScore maxScores = cdMatches.stream()
                .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                .reduce(new CombinedMatchScore(-1, -1L),
                        (s1, s2) -> new CombinedMatchScore(
                                Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                Math.max(s1.getGradScore(), s2.getGradScore())));
        LOG.info("Max scores for {} matches is {}", masksNames, maxScores);
        // update normalized score
        cdMatches.forEach(m -> m.setNormalizedScore((float) GradientAreaGapUtils.calculateNormalizedScore(
                m.getMatchingPixels(),
                m.getGradientAreaGap(),
                m.getHighExpressionArea(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        NeuronMatchesWriter<CDMatchEntity<M, T>> matchesWriter = getCDMatchesWriter();
        return matchesWriter.writeUpdates(
                cdMatches,
                Arrays.asList(
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore()), // only update the normalized score
                        m -> ImmutablePair.of("updatedDate", new Date())
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateProcessingTag(List<CDMatchEntity<M, T>> cdMatches) {
        Set<String> processingTags = Collections.singleton(args.getProcessingTag());
        return getCDMipsWriter()
                .map(cdmipsWriter -> {
                    Set<M> masksToUpdate = cdMatches.stream()
                            .map(AbstractMatchEntity::getMaskImage).collect(Collectors.toSet());
                    Set<T> targetsToUpdate = cdMatches.stream()
                            .map(AbstractMatchEntity::getMatchedImage).collect(Collectors.toSet());
                    cdmipsWriter.addProcessingTags(masksToUpdate, ProcessingType.NormalizeGradientScore, processingTags);
                    cdmipsWriter.addProcessingTags(targetsToUpdate, ProcessingType.NormalizeGradientScore, processingTags);
                    return masksToUpdate.size() + targetsToUpdate.size();
                })
                .orElse(0);
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMask(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, String maskCDMipId) {
        LOG.info("Read all color depth matches for {}", maskCDMipId);
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (args.pctPositivePixels > 0) {
            neuronsMatchScoresFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        neuronsMatchScoresFilter.addSScore("gradientAreaGap", 0);
        // return all matches for this mipID that have a gradient score
        // the "targets" filtering will be used for normalizing the score for the selected targets
        return cdsMatchesReader.readMatchesByMask(
                args.alignmentSpace,
                new DataSourceParam()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addMipID(maskCDMipId)
                        .addDatasets(args.maskDatasets)
                        .addTags(args.maskTags)
                        .addAnnotations(args.maskAnnotations)
                        .addExcludedAnnotations(args.excludedMaskAnnotations),
                new DataSourceParam()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addLibraries(args.targetsLibraries)
                        .addNames(args.targetsPublishedNames)
                        .addMipIDs(args.targetsMIPIDs)
                        .addDatasets(args.targetDatasets)
                        .addTags(args.targetTags)
                        .addAnnotations(args.targetAnnotations)
                        .addExcludedAnnotations(args.excludedTargetAnnotations),
                args.matchTags,
                /*matchExcludedTags*/null,
                neuronsMatchScoresFilter,
                Collections.singletonList(
                        new SortCriteria("normalizedScore", SortDirection.DESC)
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CompletableFuture<CDMatchEntity<M, T>>> runGradScoreComputations(M mask,
                                                                          List<CDMatchEntity<M, T>> selectedMatches,
                                                                          ColorDepthSearchAlgorithmProvider<ShapeMatchScore> gradScoreAlgorithmProvider,
                                                                          Executor executor) {
        LOG.info("Prepare gradient score computations for {} with {} matches", mask, selectedMatches.size());
        LOG.info("Load query image {}", mask);
        NeuronMIP<M> maskImage = NeuronMIPUtils.loadComputeFile(mask, ComputeFileType.InputColorDepthImage);
        if (NeuronMIPUtils.hasNoImageArray(maskImage) || CollectionUtils.isEmpty(selectedMatches)) {
            LOG.error("No image found for {}", mask);
            return Collections.emptyList();
        }
        ColorDepthSearchAlgorithm<ShapeMatchScore> gradScoreAlgorithm =
                gradScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        maskImage.getImageArray(),
                        args.maskThreshold,
                        args.borderSize);
        Set<ComputeFileType> requiredVariantTypes = gradScoreAlgorithm.getRequiredTargetVariantTypes();
        return selectedMatches.stream()
                .map(cdsMatch -> CompletableFuture.supplyAsync(() -> {
                            long startCalcTime = System.currentTimeMillis();
                            T matchedTarget = cdsMatch.getMatchedImage();
                            NeuronMIP<T> matchedTargetImage = CachedMIPsUtils.loadMIP(matchedTarget, ComputeFileType.InputColorDepthImage);
                            if (NeuronMIPUtils.hasImageArray(matchedTargetImage)) {
                                LOG.debug("Calculate grad score between {} and {}",
                                        cdsMatch.getMaskImage(), cdsMatch.getMatchedImage());
                                ShapeMatchScore gradScore = gradScoreAlgorithm.calculateMatchingScore(
                                        matchedTargetImage.getImageArray(),
                                        NeuronMIPUtils.getImageLoaders(
                                                matchedTarget,
                                                requiredVariantTypes,
                                                (n, cft) -> NeuronMIPUtils.getImageArray(CachedMIPsUtils.loadMIP(n, cft))
                                        )
                                );
                                cdsMatch.setGradientAreaGap(gradScore.getGradientAreaGap());
                                cdsMatch.setHighExpressionArea(gradScore.getHighExpressionArea());
                                cdsMatch.setNormalizedScore(gradScore.getNormalizedScore());
                                LOG.debug("Finished calculating negative score between {} and {} in {}ms",
                                        cdsMatch.getMaskImage(), cdsMatch.getMatchedImage(), System.currentTimeMillis() - startCalcTime);
                            } else {
                                cdsMatch.setGradientAreaGap(-1L);
                            }
                            return cdsMatch;
                        },
                        executor))
                .collect(Collectors.toList());
    }

}
