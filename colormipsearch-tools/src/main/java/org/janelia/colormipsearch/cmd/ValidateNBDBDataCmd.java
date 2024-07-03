package org.janelia.colormipsearch.cmd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.jacsdata.CachedDataHelper;
import org.janelia.colormipsearch.cmd.jacsdata.ColorDepthMIP;
import org.janelia.colormipsearch.cmd.jacsdata.JacsDataGetter;
import org.janelia.colormipsearch.cmd.jacsdata.PublishedDataGetter;
import org.janelia.colormipsearch.dao.AppendFieldValueHandler;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EntityField;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This command is used to export data from the database to the file system in order to upload it to S3.
 */
class ValidateNBDBDataCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateNBDBDataCmd.class);

    @Parameters(commandDescription = "Export neuron matches")
    static class ValidateCmdArgs extends AbstractCmdArgs {

        @Parameter(names = {"--jacs-url", "--data-url"}, description = "JACS data service base URL")
        String dataServiceURL;

        @Parameter(names = {"--config-url"}, description = "Config URL that contains the library name mapping")
        String configURL = "https://config.int.janelia.org/config";

        @Parameter(names = {"--authorization"},
                description = "JACS authorization - this is the value of the authorization header")
        String authorization;

        @Parameter(names = {"-as", "--alignment-space"}, description = "Alignment space")
        String alignmentSpace;

        @Parameter(names = {"-l", "--library"},
                description = "Library names from which mips or matches are selected to be validated",
                variableArity = true)
        List<String> libraries = new ArrayList<>();

        @Parameter(names = {"--validated-samples"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "If set only validate the specified samples or bodyIDs",
                variableArity = true)
        List<String> validatedSamples = new ArrayList<>();

        @Parameter(names = {"--validated-releases"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "If set only validate the specified releases",
                variableArity = true)
        List<String> validatedReleases = new ArrayList<>();

        @Parameter(names = {"--validated-tags"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "If set only validate the specified tags",
                variableArity = true)
        List<String> validatedTags = new ArrayList<>();

        @Parameter(names = {"--validated-names"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "If set only validate the specified names",
                variableArity = true)
        List<String> validatedNames = new ArrayList<>();

        @Parameter(names = {"--excluded-libraries"},
                description = "Library names that a neuron should not be part of",
                variableArity = true)
        List<String> excludedLibraries = new ArrayList<>();

        @Parameter(names = {"--no-grad-files-check"},
                description = "If set do not check input files used for gradient scoring",
                arity = 0)
        boolean noGradScoreFilesCheck = false;

        @Parameter(names = {"--processingPartitionSize", "-ps", "--libraryPartitionSize"}, description = "Processing partition size")
        int processingPartitionSize = 5000;

        @Parameter(names = {"--read-batch-size"}, description = "JACS read chunk size")
        int readBatchSize = 1000;

        @Parameter(names = {"--offset"}, description = "Offset of the data to be validated")
        long offset = 0;

        @Parameter(names = {"--size"}, description = "Size of the data to be validated")
        int size = 0;

        @Parameter(names = {"--error-tag"}, description = "Error tag")
        String errorTag;

        @Parameter(names = {"--apply-error-tag-to-em-cdmatches"},
                description = "This is meaningful only if error-tag is set to apply the error tag to the corresponding EM - LM matches as well",
                arity = 0)
        boolean applyTagToEMMatches;

        @Parameter(names = {"--apply-error-tag-to-lm-cdmatches"},
                description = "This is meaningful only if error-tag is set to apply the error tag to the corresponding LM - EM matches as well",
                arity = 0)
        boolean applyTagToLMMatches;

        ValidateCmdArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }
    }

    private static class ErrorReport {
        long nEntities;
        final List<AbstractNeuronEntity> entitiesWithErrors;
        final List<AbstractNeuronEntity> correctedEntities;

        ErrorReport(long nEntities,
                    List<AbstractNeuronEntity> entitiesWithErrors,
                    List<AbstractNeuronEntity> correctedEntities) {
            this.nEntities = nEntities;
            this.entitiesWithErrors = entitiesWithErrors;
            this.correctedEntities = correctedEntities;
        }
    }

    private final ValidateCmdArgs args;

    ValidateNBDBDataCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        this.args = new ValidateCmdArgs(commonArgs);
    }

    @Override
    ValidateCmdArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        runDataValidation();
    }

    private void runDataValidation() {
        Executor validationExecutor = CmdUtils.createCmdExecutor(args.commonArgs);
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = getDaosProvider().getNeuronMetadataDao();
        NeuronMatchesDao<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesDao = getDaosProvider().getCDMatchesDao();
        CachedDataHelper dataHelper = new CachedDataHelper(
                new JacsDataGetter(
                        args.dataServiceURL,
                        args.configURL,
                        args.authorization,
                        args.readBatchSize),
                new PublishedDataGetter(
                        getDaosProvider().getPublishedImageDao(),
                        getDaosProvider().getNeuronPublishedUrlsDao(),
                        Collections.emptyMap())
        );
        long startProcessingTime = System.currentTimeMillis();
        List<AbstractNeuronEntity> neuronEntities = neuronMetadataDao.findNeurons(
                new NeuronSelector()
                        .setAlignmentSpace(args.alignmentSpace)
                        .addSourceRefIds(args.validatedSamples)
                        .addNames(args.validatedNames)
                        .addDatasetLabels(args.validatedReleases)
                        .addTags(args.validatedTags)
                        .addLibraries(args.libraries),
                new PagedRequest()
                        .setFirstPageOffset(args.offset)
                        .setPageSize(args.size)).getResultList();
        List<CompletableFuture<ErrorReport>> allValidationJobs =
                ItemsHandling.partitionCollection(neuronEntities, args.processingPartitionSize).entrySet().stream().parallel()
                        .map(indexedPartition -> CompletableFuture
                                .supplyAsync(() -> runValidationForNeuronEntities(indexedPartition.getKey(), indexedPartition.getValue(), dataHelper), validationExecutor)
                                .thenApply(errorReport -> {
                                    processValidationReport(errorReport, neuronMetadataDao, neuronMatchesDao);
                                    return errorReport;
                                })
                        )
                        .collect(Collectors.toList());
        ErrorReport report = CompletableFuture.allOf(allValidationJobs.toArray(new CompletableFuture<?>[0]))
                .thenApply(voidResult -> allValidationJobs.stream().map(CompletableFuture::join)
                                            .reduce(new ErrorReport(0L, Collections.emptyList(), Collections.emptyList()),
                                                    this::combineErrorReport))
                .join();
        LOG.info("Finished validating {} neuron entities - found {} neurons with errors and {} that have been fixed in {}s",
                report.nEntities, report.entitiesWithErrors.size(), report.correctedEntities.size(),
                (System.currentTimeMillis()-startProcessingTime)/1000.);
    }

    private ErrorReport runValidationForNeuronEntities(int jobId,
                                                       List<AbstractNeuronEntity> neuronEntities,
                                                       CachedDataHelper dataHelper) {
        long startProcessingTime = System.currentTimeMillis();
        LOG.info("Start validating {} neuron entities from partition {}", neuronEntities.size(), jobId);
        // retrieve color depth from JACS
        Set<String> mipIds = neuronEntities.stream()
                .map(AbstractNeuronEntity::getMipId)
                .collect(Collectors.toSet());
        dataHelper.cacheCDMIPs(mipIds);
        ErrorReport errorsReport = neuronEntities.stream()
                .map(ne -> {
                    boolean isValid = validateNeuronEntity(ne, dataHelper);
                    List<AbstractNeuronEntity> entitiesWithErrors;
                    List<AbstractNeuronEntity> correctedEntities;
                    if (isValid) {
                        if (ne.hasValidationErrors()) {
                            // if previously this neuron was invalid but now is valid
                            ne.clearValidationErrors();
                            correctedEntities = Collections.singletonList(ne);
                        } else {
                            // this neuron has been valid before -> nothing to do for it
                            correctedEntities = Collections.emptyList();
                        }
                        entitiesWithErrors = Collections.emptyList();
                    } else {
                        entitiesWithErrors = Collections.singletonList(ne);
                        correctedEntities = Collections.emptyList();
                    }
                    return new ErrorReport(1, entitiesWithErrors, correctedEntities);
                })
                .reduce(new ErrorReport(0, Collections.emptyList(), Collections.emptyList()),
                        this::combineErrorReport);
        LOG.info("Finished validating {} neuron entities from partition {} - found {} neurons with errors and {} that have been fixed in {}s",
                errorsReport.nEntities, jobId, errorsReport.entitiesWithErrors.size(), errorsReport.correctedEntities.size(),
                (System.currentTimeMillis()-startProcessingTime)/1000.);
        return errorsReport;
    }

    private ErrorReport combineErrorReport(ErrorReport r1, ErrorReport r2) {
        return new ErrorReport(
                r1.nEntities + r2.nEntities,
                ImmutableList.<AbstractNeuronEntity>builder()
                        .addAll(r1.entitiesWithErrors)
                        .addAll(r2.entitiesWithErrors)
                        .build(),
                ImmutableList.<AbstractNeuronEntity>builder()
                        .addAll(r1.correctedEntities)
                        .addAll(r2.correctedEntities)
                        .build()
        );
    }

    private boolean validateNeuronEntity(AbstractNeuronEntity ne, CachedDataHelper dataHelper) {
        Set<String> errors = new HashSet<>();
        ColorDepthMIP colorDepthMIP = dataHelper.getColorDepthMIP(ne.getMipId());
        if (colorDepthMIP == null) {
            errors.add(String.format("No color depth mip in JACS for MIP %s", ne.getMipId()));
        } else {
            // check that the library is correct
            if (!colorDepthMIP.libraries.contains(ne.getLibraryName())) {
                errors.add(String.format("MIP %s is in %s but not in the %s library",
                        ne.getMipId(), colorDepthMIP.libraries, ne.getLibraryName()));
            }
            if (CollectionUtils.isNotEmpty(args.excludedLibraries) &&
                CollectionUtils.containsAny(colorDepthMIP.libraries, args.excludedLibraries)) {
                // typically if excludedLibraries is specified we want it to be only in the one that is a subset of the other;
                // for example in JACS mips from Annotator MCFO are both in MCFO and in Annotator MCFO
                // but in NeuronBridge we want the Annotator MCFO ones to only be treated as Annotator MCFO
                errors.add(String.format("MIP %s is also in %s libraries and should be both in %s and in %s",
                        ne.getMipId(), colorDepthMIP.libraries, args.libraries, args.excludedLibraries));
            }
        }
        // check file types
        checkComputeFile(ne, ComputeFileType.SourceColorDepthImage, errors);
        checkComputeFile(ne, ComputeFileType.InputColorDepthImage, errors);
        if (!args.noGradScoreFilesCheck) {
            checkComputeFile(ne, ComputeFileType.GradientImage, errors);
            checkComputeFile(ne, ComputeFileType.ZGapImage, errors);
        }
        if (errors.isEmpty()) {
            return true;
        } else {
            ne.setValidationErrors(errors);
            return false;
        }
    }

    private void checkComputeFile(AbstractNeuronEntity ne,
                                  ComputeFileType computeFileType,
                                  Collection<String> errors) {
        if (!ne.hasComputeFile(computeFileType)) {
            errors.add(String.format("Missing attribute for file type %s", computeFileType));
        } else {
            FileData fd = ne.getComputeFileData(computeFileType);
            if (!NeuronMIPUtils.exists(fd)) {
                errors.add(String.format(
                        "Compute file type %s:%s for was not found",
                        computeFileType, fd.getName()));
            }
        }
    }

    private void processValidationReport(ErrorReport validationReport,
                                         NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao,
                                         NeuronMatchesDao<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesDao) {

        ImmutableList.Builder<Function<AbstractNeuronEntity, EntityField<?>>> errorUpdatesBuilder = new ImmutableList.Builder<>();
        errorUpdatesBuilder.add(n -> new EntityField<>("validationErrors", true, n.getValidationErrors()));
        if (StringUtils.isNotBlank(args.errorTag)) {
            errorUpdatesBuilder.add(n -> new EntityField<>("tags", true, args.errorTag));
        }
        ImmutableList.Builder<Function<AbstractNeuronEntity, EntityField<?>>> correctionUpdatesBuilder = new ImmutableList.Builder<>();
        correctionUpdatesBuilder.add(n -> new EntityField<>("validationErrors", false, true, null));
        if (StringUtils.isNotBlank(args.errorTag)) {
            correctionUpdatesBuilder.add(n -> new EntityField<>("tags", true, true, args.errorTag));
        }
        long nErrorUpdates = neuronMetadataDao.updateExistingNeurons(validationReport.entitiesWithErrors, errorUpdatesBuilder.build());
        long nCorrectionUpdates = neuronMetadataDao.updateExistingNeurons(validationReport.correctedEntities, correctionUpdatesBuilder.build());
        LOG.info("Updated validation status for {} entities", nErrorUpdates + nCorrectionUpdates);
        if (StringUtils.isNotBlank(args.errorTag) && args.applyTagToEMMatches && !validationReport.entitiesWithErrors.isEmpty()) {
            long nMatchesUpdates = neuronMatchesDao.updateAll(
                    new NeuronsMatchFilter<CDMatchEntity<?, ?>>()
                            .setMaskEntityIds(validationReport.entitiesWithErrors.stream().map(n -> n.getEntityId()).collect(Collectors.toSet())),
                    ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton(args.errorTag)))
            );
            LOG.info("Marked {} EM CD matches as bad", nMatchesUpdates);
        }
        if (StringUtils.isNotBlank(args.errorTag) && args.applyTagToLMMatches && !validationReport.entitiesWithErrors.isEmpty()) {
            long nMatchesUpdates = neuronMatchesDao.updateAll(
                    new NeuronsMatchFilter<CDMatchEntity<?, ?>>()
                            .setTargetEntityIds(validationReport.entitiesWithErrors.stream().map(n -> n.getEntityId()).collect(Collectors.toSet())),
                    ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton(args.errorTag)))
            );
            LOG.info("Marked {} LM CD matches as bad", nMatchesUpdates);
        }
    }
}
