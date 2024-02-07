package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProvider;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithmProviderFactory;
import org.janelia.colormipsearch.cds.CombinedMatchScore;
import org.janelia.colormipsearch.cds.GradientAreaGapUtils;
import org.janelia.colormipsearch.cds.ShapeMatchScore;
import org.janelia.colormipsearch.cmd.cdsprocess.ColorMIPProcessUtils;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.db.DBNeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fs.JSONNeuronMatchesWriter;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.datarequests.SortDirection;
import org.janelia.colormipsearch.image.ImageAccess;
import org.janelia.colormipsearch.image.type.ByteArrayRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to calculate the gradient scores.
 */
class CalculateGradientScoresCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CalculateGradientScoresCmd.class);

    @Parameters(commandDescription = "Color depth search for a batch of MIPs")
    static class GradientScoresArgs extends AbstractColorDepthMatchArgs {

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space: {JRC2018_Unisex_20x_HR, JRC2018_VNC_Unisex_40x_DS} ", required = true)
        String alignmentSpace;

        @Parameter(names = {"--masks-libraries", "-md"}, required = true, variableArity = true,
                converter = ListArg.ListArgConverter.class,
                description = "Masks libraries; for JSON results this is interpreted as the location of the match files")
        List<ListArg> masksLibraries;

        @Parameter(names = {"--masks-published-names"}, description = "Masks published names to be selected for gradient scoring",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksPublishedNames = new ArrayList<>();

        @Parameter(names = {"--masks-mips"}, description = "Selected mask MIPs",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> masksMIPIDs;

        @Parameter(names = {"--masks-datasets"}, description = "Datasets associated with the mask of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> maskDatasets = new ArrayList<>();

        @Parameter(names = {"--masks-tags"}, description = "Tags associated with the mask of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> maskTags = new ArrayList<>();

        @Parameter(names = {"--targets-datasets"}, description = "Datasets associated with the target of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetDatasets = new ArrayList<>();

        @Parameter(names = {"--targets-tags"}, description = "Tags associated with the target of the match to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetTags = new ArrayList<>();

        @Parameter(names = {"--targets-libraries"}, description = "Target libraries for the selected matches",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsLibraries;

        @Parameter(names = {"--targets-published-names"}, description = "Selected target names",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsPublishedNames;

        @Parameter(names = {"--targets-mips"}, description = "Selected target MIPs",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> targetsMIPIDs;

        @Parameter(names = {"--match-tags"}, description = "Match tags to be scored",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> matchTags = new ArrayList<>();

        @Parameter(names = {"--nBestLines"},
                description = "Specifies the number of the top distinct lines to be used for gradient score")
        int numberOfBestLines;

        @Parameter(names = {"--nBestSamplesPerLine"},
                description = "Specifies the number of the top distinct samples within a line to be used for gradient score")
        int numberOfBestSamplesPerLine;

        @Parameter(names = {"--nBestMatchesPerSample"},
                description = "Number of best matches for each sample to be used for gradient scoring")
        int numberOfBestMatchesPerSample;

        GradientScoresArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private final GradientScoresArgs args;
    private final Supplier<Long> cacheSizeSupplier;
    private final ObjectMapper mapper;

    CalculateGradientScoresCmd(String commandName,
                               CommonArgs commonArgs,
                               Supplier<Long> cacheSizeSupplier) {
        super(commandName);
        this.args = new GradientScoresArgs(commonArgs);
        this.cacheSizeSupplier = cacheSizeSupplier;
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    GradientScoresArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        // initialize the cache
        CachedMIPsUtils.initializeCache(cacheSizeSupplier.get());
        // run gradient scoring
        calculateAllGradientScores(new ByteArrayRGBPixelType(), new UnsignedIntType());
    }

    private <P extends RGBPixelType<P>, G extends IntegerType<G>> void calculateAllGradientScores(P rgbPixel, G grayPixel) {
        long startTime = System.currentTimeMillis();
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore, P, G> gradScoreAlgorithmProvider = ColorDepthSearchAlgorithmProviderFactory.createShapeMatchCDSAlgorithmProvider(
                args.mirrorMask,
                args.dataThreshold,
                args.negativeRadius,
                args.getColorScaleAndLabelRegionCondition(),
                loadQueryROIMask(args.queryROIMaskName)
        );
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
                        long nPublishedLines = cdMatchesForMask.stream()
                                .map(cdm -> cdm.getMatchedImage().getPublishedName())
                                .distinct()
                                .count();
                        // calculate the grad scores
                        LOG.info("Calculate grad scores for {} matches ({} lines) of {}", cdMatchesForMask.size(), nPublishedLines, maskIdToProcess);
                        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> cdMatchesWithGradScores = calculateGradientScores(
                                gradScoreAlgorithmProvider,
                                cdMatchesForMask,
                                rgbPixel,
                                grayPixel,
                                executor);
                        LOG.info("Completed grad scores for {} matches of {}", cdMatchesWithGradScores.size(), maskIdToProcess);
                        long writtenUpdates = updateCDMatches(cdMatchesWithGradScores);
                        LOG.info("Updated {} grad scores for {} matches of {}", writtenUpdates, cdMatchesWithGradScores.size(), maskIdToProcess);
                    });
                    LOG.info("Finished partition {} ({} items) in {}s - memory usage {}M out of {}M",
                            indexedPartition.getKey(),
                            indexedPartition.getValue().size(),
                            (System.currentTimeMillis() - startProcessingPartitionTime) / 1000.,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                            (Runtime.getRuntime().totalMemory() / _1M));
                });
        LOG.info("Finished calculating gradient scores for {} items in {}s - memory usage {}M out of {}M",
                size,
                (System.currentTimeMillis() - startTime) / 1000.,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / _1M + 1, // round up
                (Runtime.getRuntime().totalMemory() / _1M));
    }

    /**
     * The ROI mask is typically the hemibrain mask that should be applied when the color depth search is done from LM to EM.
     *
     * @param queryROIMask the location of the ROI mask
     * @return
     */
    private ImageAccess<?> loadQueryROIMask(String queryROIMask) {
        if (StringUtils.isBlank(queryROIMask)) {
            return null;
        } else {
            return NeuronMIPUtils.loadMaskFromFileData(FileData.fromString(queryROIMask));
        }
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

    /**
     * The method calculates and updates the gradient scores for all color depth matches of the given mask MIP ID.
     *
     * @param gradScoreAlgorithmProvider grad score algorithm provider
     * @param cdMatches                  color depth matches for which the grad score will be computed
     * @param executor                   task executor
     * @param <M>                        mask type
     * @param <T>                        target type
     */
    @SuppressWarnings("unchecked")
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, P extends RGBPixelType<P>, G>
    List<CDMatchEntity<M, T>> calculateGradientScores(
            ColorDepthSearchAlgorithmProvider<ShapeMatchScore, P, G> gradScoreAlgorithmProvider,
            List<CDMatchEntity<M, T>> cdMatches,
            P rgbPixel,
            G grayPixel,
            Executor executor) {
        // group the matches by the mask input file - this is because we do not want to mix FL and non-FL neuron images for example
        List<GroupedMatchedEntities<M, T, CDMatchEntity<M, T>>> selectedMatchesGroupedByInput =
                MatchEntitiesGrouping.simpleGroupByMaskFields(
                        cdMatches,
                        Arrays.asList(
                                AbstractNeuronEntity::getMipId,
                                m -> m.getComputeFileName(ComputeFileType.InputColorDepthImage)
                        )
                );
        List<CompletableFuture<CDMatchEntity<M, T>>> gradScoreComputations = selectedMatchesGroupedByInput.stream()
                .flatMap(selectedMaskMatches -> runGradScoreComputations(
                        selectedMaskMatches.getKey(),
                        selectedMaskMatches.getItems(),
                        gradScoreAlgorithmProvider,
                        rgbPixel,
                        grayPixel,
                        executor
                ).stream())
                .collect(Collectors.toList());
        // wait for all computation to finish
        List<CDMatchEntity<M, T>> matchesWithGradScores = gradScoreComputations.stream()
                .map(CompletableFuture::join)
                .filter(CDMatchEntity::hasGradScore)
                .collect(Collectors.toList());

        updateNormalizedScores(matchesWithGradScores);

        return matchesWithGradScores;
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> long updateCDMatches(List<CDMatchEntity<M, T>> cdMatches) {
        NeuronMatchesWriter<CDMatchEntity<M, T>> matchesWriter = getCDMatchesWriter();
        return matchesWriter.writeUpdates(
                cdMatches,
                Arrays.asList(
                        m -> ImmutablePair.of("gradientAreaGap", m.getGradientAreaGap()),
                        m -> ImmutablePair.of("highExpressionArea", m.getHighExpressionArea()),
                        m -> ImmutablePair.of("normalizedScore", m.getNormalizedScore())
                ));
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    List<CDMatchEntity<M, T>> getCDMatchesForMask(NeuronMatchesReader<CDMatchEntity<M, T>> cdsMatchesReader, String maskCDMipId) {
        LOG.info("Read all color depth matches for {}", maskCDMipId);
        ScoresFilter neuronsMatchScoresFilter = new ScoresFilter();
        if (args.pctPositivePixels > 0) {
            neuronsMatchScoresFilter.addSScore("matchingPixelsRatio", args.pctPositivePixels / 100);
        }
        List<CDMatchEntity<M, T>> allCDMatches = cdsMatchesReader.readMatchesByMask(
                args.alignmentSpace,
                /* maskLibraries */null,
                /* maskPublishedNames */null,
                Collections.singletonList(maskCDMipId),
                args.maskDatasets,
                args.maskTags,
                /*maskExcludedTags*/null,
                args.targetsLibraries,
                args.targetsPublishedNames,
                args.targetsMIPIDs,
                args.targetDatasets,
                args.targetTags,
                /*targetExcludedTags*/null,
                args.matchTags,
                /*matchExcludedTags*/null,
                neuronsMatchScoresFilter,
                Collections.singletonList(
                        new SortCriteria("normalizedScore", SortDirection.DESC)
                ));
        // select best matches to process
        LOG.info("Select best color depth matches for {} out of {} total matches", maskCDMipId, allCDMatches.size());
        return ColorMIPProcessUtils.selectBestMatches(
                allCDMatches,
                args.numberOfBestLines,
                args.numberOfBestSamplesPerLine,
                args.numberOfBestMatchesPerSample
        );
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, P extends RGBPixelType<P>, G>
    List<CompletableFuture<CDMatchEntity<M, T>>> runGradScoreComputations(M mask,
                                                                          List<CDMatchEntity<M, T>> selectedMatches,
                                                                          ColorDepthSearchAlgorithmProvider<ShapeMatchScore, P, G> gradScoreAlgorithmProvider,
                                                                          P rgbPixel,
                                                                          G grayPixel,
                                                                          Executor executor) {
        LOG.info("Prepare gradient score computations for {} with {} matches", mask, selectedMatches.size());
        LOG.info("Load query image {}", mask);
        NeuronMIP<M, P> maskImage = NeuronMIPUtils.loadRGBComputeFile(mask, ComputeFileType.InputColorDepthImage, rgbPixel);
        if (NeuronMIPUtils.hasNoImageArray(maskImage) || CollectionUtils.isEmpty(selectedMatches)) {
            LOG.error("No image found for {}", mask);
            return Collections.emptyList();
        }
        ColorDepthSearchAlgorithm<ShapeMatchScore, P, G> gradScoreAlgorithm =
                gradScoreAlgorithmProvider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        maskImage.getImageArray(),
                        args.maskThreshold);
        Set<ComputeFileType> requiredRGBVariantTypes = gradScoreAlgorithm.getRequiredTargetRGBVariantTypes();
        Set<ComputeFileType> requiredGrayVariantTypes = gradScoreAlgorithm.getRequiredTargetGrayVariantTypes();
        return selectedMatches.stream()
                .map(cdsMatch -> CompletableFuture.supplyAsync(() -> {
                            long startCalcTime = System.currentTimeMillis();
                            T matchedTarget = cdsMatch.getMatchedImage();
                            NeuronMIP<T, P> matchedTargetImage =
                                    CachedMIPsUtils.loadRGBMIP(matchedTarget, ComputeFileType.InputColorDepthImage, rgbPixel);
                            if (NeuronMIPUtils.hasImageArray(matchedTargetImage)) {
                                LOG.debug("Calculate grad score between {} and {}",
                                        cdsMatch.getMaskImage(), cdsMatch.getMatchedImage());
                                ShapeMatchScore gradScore = gradScoreAlgorithm.calculateMatchingScore(
                                        matchedTargetImage.getImageArray(),
                                        ColorMIPProcessUtils.getRGBVariantImagesSuppliers(
                                                requiredRGBVariantTypes,
                                                matchedTarget,
                                                rgbPixel
                                        ),
                                        ColorMIPProcessUtils.getGrayVariantImagesSuppliers(
                                                requiredGrayVariantTypes,
                                                matchedTarget,
                                                grayPixel
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

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> void updateNormalizedScores(List<CDMatchEntity<M, T>> cdMatches) {
        // get max scores for normalization
        CombinedMatchScore maxScores = cdMatches.stream()
                .map(m -> new CombinedMatchScore(m.getMatchingPixels(), m.getGradScore()))
                .reduce(new CombinedMatchScore(-1, -1L),
                        (s1, s2) -> new CombinedMatchScore(
                                Math.max(s1.getPixelMatches(), s2.getPixelMatches()),
                                Math.max(s1.getGradScore(), s2.getGradScore())));
        // update normalized score
        cdMatches.forEach(m -> m.setNormalizedScore((float) GradientAreaGapUtils.calculateNormalizedScore(
                m.getMatchingPixels(),
                m.getGradientAreaGap(),
                m.getHighExpressionArea(),
                maxScores.getPixelMatches(),
                maxScores.getGradScore()
        )));
    }

}
