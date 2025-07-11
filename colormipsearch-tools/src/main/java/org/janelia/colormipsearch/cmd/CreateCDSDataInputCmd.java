package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.jacsdata.ColorDepthMIP;
import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBCDMIPsWriter;
import org.janelia.colormipsearch.dataio.db.DBCheckedCDMIPsWriter;
import org.janelia.colormipsearch.dataio.fs.JSONCDMIPsWriter;
import org.janelia.colormipsearch.mips.FileDataUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.Gender;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepare the data input for Color Depth Search. These data input could potentially be a Mongo database or a JSON file
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
class CreateCDSDataInputCmd extends AbstractCmd {

    private static final Logger LOG = LoggerFactory.getLogger(CreateCDSDataInputCmd.class);

    private static final int DEFAULT_PAGE_LENGTH = 10000;

    public static class ChannelBaseValidator implements IValueValidator<Integer> {
        @Override
        public void validate(String name, Integer value) throws ParameterException {
            if (value < 0 || value > 1) {
                throw new ParameterException("Invalid channel base - supported values are {0, 1}");
            }
        }
    }

    @Parameters(commandDescription = "Create input data for CDS")
    static class CreateColorDepthSearchDataInputArgs extends AbstractCmdArgs {
        @Parameter(names = {"--jacs-url", "--data-url", "--jacsURL"}, description = "JACS data service base URL")
        String dataServiceURL;

        @Parameter(names = {"--authorization"}, description = "JACS authorization - this is the value of the authorization header")
        String authorization;

        @Parameter(names = {"--alignment-space", "-as"}, description = "Alignment space: {JRC2018_Unisex_20x_HR, JRC2018_VNC_Unisex_40x_DS} ", required = true)
        String alignmentSpace;

        @Parameter(names = {"--library", "-l"},
                description = "Which library to extract such as {flyem_hemibrain_1_2_1, flyem_manc_1_0, flylight_split_gal4_published, flylight_gen1_mcfo_published}. " +
                        "The format is <libraryName>[:<offset>[:<length>]]",
                required = true, converter = ListArg.ListArgConverter.class)
        ListArg library;

        @Parameter(names = {"--releases", "-r"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "Which specific releases to be included.",
                variableArity = true)
        List<String> releases;

        @Parameter(names = {"--mips"},
                listConverter = ListValueAsFileArgConverter.class,
                description = "If set only create inputs for these specific mips",
                variableArity = true)
        List<String> includedMIPs;

        @Parameter(names = {"--librariesVariants", "--libraryVariants"},
                description = "Libraries variants descriptors. " +
                        "A library variant contains library name, variant type, location and suffix separated by colon , e.g., " +
                        "flylight_gen1_mcfo_published:segmentation:/libDir/mcfo/segmentation:_CDM",
                converter = LibraryVariantArg.LibraryVariantArgConverter.class,
                validateValueWith = LibraryVariantArg.ListLibraryVariantArgValidator.class,
                variableArity = true)
        List<LibraryVariantArg> libraryVariants;

        @DynamicParameter(names = "--variant-filetype-mapping", description = "Dynamic variant to file type mapping")
        Map<String, String> variantFileTypeMapping = new HashMap<String, String>() {{
            put("source", ComputeFileType.SourceColorDepthImage.name());
            put("source_cdm", ComputeFileType.SourceColorDepthImage.name());
            put("cdm", ComputeFileType.InputColorDepthImage.name());
            put("searchable", ComputeFileType.InputColorDepthImage.name());
            put("searchable_neurons", ComputeFileType.InputColorDepthImage.name());
            put("segmentation", ComputeFileType.InputColorDepthImage.name());
            put("3d-segmentation", ComputeFileType.Vol3DSegmentation.name());
            put("vol-segmentation", ComputeFileType.Vol3DSegmentation.name());
            put("fl", ComputeFileType.InputColorDepthImage.name());
            put("grad", ComputeFileType.GradientImage.name());
            put("gradient", ComputeFileType.GradientImage.name());
            put("zgap", ComputeFileType.ZGapImage.name());
            put("rgb", ComputeFileType.ZGapImage.name());
            put("junk", ComputeFileType.JunkImage.name());
        }};

        @Parameter(names = "--included-libraries", variableArity = true, description = "If set, MIPs should also be in all these libraries")
        Set<String> includedLibraries;

        @Parameter(names = "--excluded-libraries", variableArity = true, description = "If set, MIPs should not be part of any of these libraries")
        Set<String> excludedLibraries;

        @Parameter(names = {"--datasets"}, description = "Which datasets to extract",
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true)
        List<String> datasets;

        @Parameter(names = {"--tag"}, description = "Tag to assign to the imported mips")
        String tag;

        @Parameter(names = {"--junk-tag"}, description = "Tag to assign to the junk variants")
        String junkTag;

        @Parameter(names = "--segmentation-channel-base", validateValueWith = ChannelBaseValidator.class,
                description = "Segmentation channel base (0 or 1)")
        int segmentedImageChannelBase = 1;

        @Parameter(names = {"--included-published-names"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "Comma-delimited list of published names to be processed")
        List<String> includedPublishedNames;

        @Parameter(names = {"--included-neurons"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "Comma-delimited list of LM slide codes or EM body ids to only be included")
        List<String> includedNeurons;

        @Parameter(names = {"--excluded-neurons"},
                listConverter = ListValueAsFileArgConverter.class,
                variableArity = true,
                description = "Comma-delimited list of LM slide codes or EM body ids to be excluded from the requested list")
        List<String> excludedNeurons;

        @Parameter(names = {"--output-filename"}, description = "Output file name")
        String outputFileName;

        @Parameter(names = {"--match-neuron-state"},
                description = "Match neuron state",
                arity = 0)
        boolean matchNeuronState = false;

        @Parameter(names = {"--for-update"},
                description = "If entry (or file exists when persisting to the filesystem) update the entry",
                arity = 0)
        boolean forUpdate;

        CreateColorDepthSearchDataInputArgs(CommonArgs commonArgs) {
            super(commonArgs);
        }

        String getOutputFileName() {
            return StringUtils.defaultIfBlank(outputFileName, library.input);
        }
    }

    static class LibraryPathsArgs {
        ListArg library;
        List<LibraryVariantArg> libraryVariants;

        List<LibraryVariantArg> listLibraryVariants() {
            return CollectionUtils.isNotEmpty(libraryVariants) ? libraryVariants : Collections.emptyList();
        }

        Optional<LibraryVariantArg> getLibraryVariant(String variantType) {
            return listLibraryVariants().stream()
                    .filter(lv -> lv.variantType.equals(variantType))
                    .findFirst();
        }

        String getLibraryName() {
            return library != null ? library.input : null;
        }

        @Override
        public String toString() {
            return getLibraryName() + ";" + listLibraryVariants().toString();
        }
    }

    private final CreateColorDepthSearchDataInputArgs args;
    private final ObjectMapper mapper;

    CreateCDSDataInputCmd(String commandName, CommonArgs commonArgs) {
        super(commandName);
        args = new CreateColorDepthSearchDataInputArgs(commonArgs);
        this.mapper = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        ;
    }

    @Override
    CreateColorDepthSearchDataInputArgs getArgs() {
        return args;
    }

    @Override
    void execute() {
        Map<String, List<LibraryVariantArg>> libraryVariants = getLibraryVariants();

        LibraryPathsArgs lpaths = new LibraryPathsArgs();
        lpaths.library = args.library;
        lpaths.libraryVariants = libraryVariants.get(lpaths.getLibraryName());

        CDMIPsWriter mipsWriter = getCDSInputWriter();

        Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedSearchableMips = getIndexedVariants(lpaths, ComputeFileType.InputColorDepthImage);
        Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedJunkMips = getIndexedVariants(lpaths, ComputeFileType.JunkImage);

        if (StringUtils.isNotBlank(args.dataServiceURL)) {
            Client httpClient = HttpHelper.createClient();
            WebTarget serverEndpoint = httpClient.target(args.dataServiceURL);
            createColorDepthSearchInputData(
                    lpaths.getLibraryName(),
                    lpaths.libraryVariants,
                    indexedSearchableMips,
                    indexedJunkMips,
                    getAsSet(args.includedPublishedNames),
                    getAsSet(args.includedNeurons),
                    getAsSet(args.excludedNeurons),
                    serverEndpoint,
                    mipsWriter
            );
        } else {
            LibraryVariantArg sourceLibraryMipsVariant = getVariantForFileType(lpaths, ComputeFileType.SourceColorDepthImage);
            LOG.info("Path to the source MIPs: {}", sourceLibraryMipsVariant);

            if (CollectionUtils.isEmpty(sourceLibraryMipsVariant.variantPaths)) {
                throw new IllegalStateException("Source variants must be defined. " +
                        "If this is an LM library retrieve the source variants from JACS. " +
                        "For new EM library set the the source CDMs location");
            }
            List<MIPsHandlingUtils.MIPsStore> sourceMIPsStores = MIPsHandlingUtils.listLibraryImageFiles(
                    lpaths.getLibraryName(),
                    sourceLibraryMipsVariant.variantPaths,
                    sourceLibraryMipsVariant.variantIgnoredPattern,
                    sourceLibraryMipsVariant.variantNameSuffix);
            Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedSourceMips = MIPsHandlingUtils.indexMIPStores(
                    sourceMIPsStores
            );

            createColorDepthSearchInputDataFromLocalData(
                    lpaths.getLibraryName(),
                    lpaths.libraryVariants,
                    indexedSourceMips,
                    indexedSearchableMips,
                    indexedJunkMips,
                    getAsSet(args.includedPublishedNames),
                    getAsSet(args.excludedNeurons),
                    mipsWriter
            );
        }
    }

    private Map<String, List<LibraryVariantArg>> getLibraryVariants() {
        Map<String, List<LibraryVariantArg>> libraryVariants;
        if (CollectionUtils.isEmpty(args.libraryVariants)) {
            libraryVariants = Collections.emptyMap();
        } else {
            libraryVariants = args.libraryVariants.stream().collect(Collectors.groupingBy(lv -> lv.libraryName, Collectors.toList()));
        }
        return libraryVariants;
    }

    private Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> getIndexedVariants(LibraryPathsArgs lpaths, ComputeFileType variantFileType) {
        LOG.info("Get path {} paths from {}", variantFileType, lpaths);
        LibraryVariantArg mipsVariant = getVariantForFileType(lpaths, variantFileType);
        if (CollectionUtils.isEmpty(mipsVariant.variantPaths)) {
            LOG.info("No variant paths set for {} in {}", variantFileType, mipsVariant);
            return Collections.emptyMap();
        }
        List<MIPsHandlingUtils.MIPsStore> mipsStores = MIPsHandlingUtils.listLibraryImageFiles(
                lpaths.getLibraryName(),
                mipsVariant.variantPaths,
                mipsVariant.variantIgnoredPattern,
                mipsVariant.variantNameSuffix
        );
        LOG.info("Import {} MIPS from {}", variantFileType, lpaths);
        Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedMips = MIPsHandlingUtils.indexMIPStores(
                mipsStores
        );
        LOG.info("Found {} mips for {}", indexedMips.size(), variantFileType);
        return indexedMips;
    }

    private Set<String> getAsSet(List<String> aList) {
        if (CollectionUtils.isEmpty(aList)) {
            return Collections.emptySet();
        } else {
            return new HashSet<>(aList);
        }
    }

    private void createColorDepthSearchInputData(String libraryName,
                                                 List<LibraryVariantArg> libraryVariants,
                                                 Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedSearchableMips,
                                                 Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedJunkMips,
                                                 Set<String> includedPublishedNames,
                                                 Set<String> includedNeurons,
                                                 Set<String> excludedNeurons,
                                                 WebTarget serverEndpoint,
                                                 CDMIPsWriter cdmipsWriter) {

        int cdmsCount = countColorDepthMips(
                serverEndpoint,
                args.authorization,
                args.alignmentSpace,
                libraryName,
                args.datasets,
                args.releases,
                args.includedMIPs);
        LOG.info("Found {} entities in library {} with alignment space {}{}",
                cdmsCount, libraryName, args.alignmentSpace, CollectionUtils.isNotEmpty(args.datasets) ? " for datasets " + args.datasets : "");
        if (CollectionUtils.isNotEmpty(includedPublishedNames)) {
            LOG.info("Include only {} published names", includedPublishedNames.size());
        }
        if (CollectionUtils.isNotEmpty(includedNeurons)) {
            LOG.info("Include only {} neurons", includedNeurons);
        }
        if (CollectionUtils.isNotEmpty(excludedNeurons)) {
            LOG.info("Exclude {} neurons", excludedNeurons);
        }

        Function<ColorDepthMIP, String> libraryNameExtractor = cdmip -> cdmip.findLibrary(libraryName);
        Function<ColorDepthMIP, InputCDMipNeuron<ColorDepthMIP, ? extends AbstractNeuronEntity>> cdmipToNeuronEntity =
                MIPsHandlingUtils.isEmLibrary(libraryName)
                        ? cdmip -> asEMNeuron(cdmip, libraryNameExtractor)
                        : cdmip -> asLMNeuron(cdmip, libraryNameExtractor);

        cdmipsWriter.open();
        for (int pageOffset = 0; pageOffset < cdmsCount; pageOffset += DEFAULT_PAGE_LENGTH) {
            int currentPageSize = Math.min(DEFAULT_PAGE_LENGTH, cdmsCount - pageOffset);
            List<ColorDepthMIP> cdmipsPage = retrieveColorDepthMipsWithSamples(
                    serverEndpoint,
                    args.authorization,
                    args.alignmentSpace,
                    libraryName,
                    args.datasets,
                    args.releases,
                    args.includedMIPs,
                    pageOffset,
                    currentPageSize);
            LOG.info("Process {} entries from {} to {} out of {}", cdmipsPage.size(), pageOffset, pageOffset + currentPageSize, cdmsCount);
            List<AbstractNeuronEntity> cdNeurons = cdmipsPage.stream()
                    .filter(cdmip -> checkLibraries(cdmip, args.includedLibraries, args.excludedLibraries))
                    .map(cdmipToNeuronEntity)
                    .filter(cdmip -> cdmip.getNeuronMetadata().isValid()) // skip invalid mips
                    .filter(cdmip -> CollectionUtils.isEmpty(includedPublishedNames) || CollectionUtils.containsAny(includedPublishedNames, cdmip.getNeuronMetadata().getPublishedName()))
                    .filter(cdmip -> CollectionUtils.isEmpty(includedNeurons) || CollectionUtils.containsAny(includedNeurons, cdmip.getNeuronMetadata().getNeuronId()))
                    .filter(cdmip -> CollectionUtils.isEmpty(excludedNeurons) || !CollectionUtils.containsAny(excludedNeurons, cdmip.getNeuronMetadata().getNeuronId()))
                    .flatMap(cdmip -> {
                        List<? extends AbstractNeuronEntity> searchableMIPs = MIPsHandlingUtils.lookupSearchableNeuronImages(
                                cdmip.getNeuronMetadata(),
                                cdmip.getSourceMIP().objective,
                                MIPsHandlingUtils.getColorChannel(cdmip.getSourceMIP()),
                                ComputeFileType.InputColorDepthImage,
                                indexedSearchableMips,
                                args.matchNeuronState,
                                args.segmentedImageChannelBase,
                                false);
                        List<? extends AbstractNeuronEntity> junkMIPs = MIPsHandlingUtils.lookupSearchableNeuronImages(
                                cdmip.getNeuronMetadata(),
                                cdmip.getSourceMIP().objective,
                                MIPsHandlingUtils.getColorChannel(cdmip.getSourceMIP()),
                                ComputeFileType.JunkImage,
                                indexedJunkMips,
                                args.matchNeuronState,
                                args.segmentedImageChannelBase,
                                false);
                        return Stream.concat(searchableMIPs.stream(), junkMIPs.stream())
                                .map(n -> new InputCDMipNeuron<>(cdmip.getSourceMIP(), n));
                    })
                    .peek(cdmip -> populateOtherComputeFilesFromInput(
                            cdmip.getNeuronMetadata(),
                            EnumSet.of(ComputeFileType.GradientImage, ComputeFileType.ZGapImage, ComputeFileType.Vol3DSegmentation),
                            libraryVariants))
                    .peek(cdmip -> this.updateTag(cdmip.getNeuronMetadata()))
                    .map(InputCDMipNeuron::getNeuronMetadata)
                    .collect(Collectors.toList());
            LOG.info("Save {} entries from {} to {} out of {}", cdNeurons.size(), pageOffset, pageOffset + currentPageSize, cdmsCount);
            cdmipsWriter.write(cdNeurons);
        }
        cdmipsWriter.close();
    }

    private <N extends AbstractNeuronEntity> void populateOtherComputeFilesFromInput(N neuronEntity,
                                                                                     Set<ComputeFileType> computeFileTypes,
                                                                                     List<LibraryVariantArg> libraryVariants) {
        String searchableMIPFile;
        if (neuronEntity.hasComputeFile(ComputeFileType.InputColorDepthImage)) {
            searchableMIPFile = new File(neuronEntity.getComputeFileName(ComputeFileType.InputColorDepthImage)).getName();
        } else {
            // don't attempt to populate other variants if there are no useful MIPs for color depth search
            return;
        }
        String searchableMIPBaseName = RegExUtils.replacePattern(searchableMIPFile, "(?:_CDM)?\\..*$", "");
        String[] searchableMIPNameComps = StringUtils.split(searchableMIPBaseName, "-_");
        StringBuilder patternBuilder = new StringBuilder(".*")
                .append(neuronEntity.getNeuronId()).append(".*");
        int maxIndexingComp;
        String indexingCompSeparators;
        Predicate<String> objectiveFilter;
        if (MIPsHandlingUtils.isEmLibrary(neuronEntity.getLibraryName())) {
            maxIndexingComp = 1; // only bodyID
            indexingCompSeparators = "_-"; // for EM we use both underscore and hyphen to delimit components used for indexing
            objectiveFilter = (n) -> true;
        } else {
            maxIndexingComp = 2; // line and slideCode
            indexingCompSeparators = "-"; // for LM we only use hyphen to delimit components used for indexing
            if (searchableMIPNameComps.length > 1) {
                LMNeuronEntity lmNeuronEntity = (LMNeuronEntity) neuronEntity;
                objectiveFilter = (n) -> {
                    String objectiveFromEntity = lmNeuronEntity.getObjective();
                    String objectiveFromFN = MIPsHandlingUtils.extractObjectiveFromImageName(n, lmNeuronEntity.getAlignmentSpace());
                    boolean match = MIPsHandlingUtils.matchMIPObjectiveWithSegmentedImageObjective(objectiveFromEntity, objectiveFromFN);
                    if (!match) {
                        LOG.debug("LM Neuron Entity objective {} and the {} objective from variant name {}, do not match for {}",
                                   objectiveFromEntity, objectiveFromFN, n, lmNeuronEntity);
                    }
                    return match;
                };
                patternBuilder.append("(?:").append(lmNeuronEntity.getObjective()).append(")?") // make the objective optional
                        .append(".*");
                patternBuilder
                        .append(searchableMIPNameComps[searchableMIPNameComps.length - 2])
                        .append("[-_]");
            } else {
                objectiveFilter = (n) -> true;
                LOG.error("Searchable MIP name '{}' does not have enough components so we may not be able to infer other variants: ",
                        searchableMIPBaseName);
            }
        }
        patternBuilder.append(searchableMIPNameComps[searchableMIPNameComps.length - 1]);
        // add searchable MIP name to the pattern
        patternBuilder.insert(0, "(?:")
                .append(")|(?:.*")
                .append(searchableMIPBaseName)
                .append(".*)");
        Pattern variantPattern = Pattern.compile(patternBuilder.toString());
        libraryVariants.stream()
                .filter(variant -> computeFileTypes.contains(ComputeFileType.fromName(args.variantFileTypeMapping.get(variant.variantType))))
                .forEach(variant -> {
                    ComputeFileType variantFileType = ComputeFileType.fromName(args.variantFileTypeMapping.get(variant.variantType));
                    List<FileData> variantsFiles = FileDataUtils.lookupVariantFileData(
                            variant.variantPaths,
                            neuronEntity.getNeuronId(),
                            maxIndexingComp,
                            indexingCompSeparators,
                            variantPattern
                    );
                    LOG.debug("Found variants {} for {} in {}", variantsFiles, variantFileType, neuronEntity);
                    if (variantsFiles.isEmpty()) {
                        neuronEntity.setComputeFileData(variantFileType, null);
                    } else {
                        variantsFiles.stream()
                                .filter(vfd -> objectiveFilter.test(vfd.getName()))
                                .forEach(vfd -> neuronEntity.setComputeFileData(variantFileType, vfd));
                    }
                });
    }

    private CDMIPsWriter getCDSInputWriter() {
        if (args.commonArgs.resultsStorage == StorageType.DB) {
            if (args.forUpdate) {
                // if update flag is set check if entry exists before creating a new one
                return new DBCheckedCDMIPsWriter(getDaosProvider().getNeuronMetadataDao());
            } else {
                return new DBCDMIPsWriter(getDaosProvider().getNeuronMetadataDao());
            }
        } else {
            return new JSONCDMIPsWriter(args.getOutputDir(),
                    args.getOutputFileName(),
                    args.library.offset,
                    args.library.length,
                    args.forUpdate,
                    mapper);
        }
    }

    private boolean checkLibraries(ColorDepthMIP cdmip, Set<String> includedLibraries, Set<String> excludedLibraries) {
        if (CollectionUtils.isNotEmpty(includedLibraries)) {
            if (!cdmip.libraries.containsAll(includedLibraries)) {
                return false;
            }
        }
        if (CollectionUtils.isNotEmpty(excludedLibraries)) {
            return cdmip.libraries.stream().noneMatch(excludedLibraries::contains);
        }
        return true;
    }

    private InputCDMipNeuron<ColorDepthMIP, EMNeuronEntity> asEMNeuron(ColorDepthMIP cdmip, Function<ColorDepthMIP, String> libraryNameExtractor) {
        String libraryName = libraryNameExtractor.apply(cdmip);
        EMNeuronEntity neuronEntity = new EMNeuronEntity();
        neuronEntity.setMipId(cdmip.id);
        neuronEntity.setAlignmentSpace(cdmip.alignmentSpace);
        neuronEntity.setLibraryName(libraryName);
        neuronEntity.setSourceRefId(cdmip.emBodyRef);
        neuronEntity.setPublishedName(cdmip.emBodyId());
        neuronEntity.setNeuronInstance(cdmip.neuronInstance);
        neuronEntity.setNeuronType(cdmip.neuronType);
        neuronEntity.setNeuronTerms(cdmip.emTerms());
        neuronEntity.addDatasetLabel(cdmip.emDataset());
        // set source color depth image
        neuronEntity.setComputeFileData(ComputeFileType.SourceColorDepthImage, FileData.asFileFromString(cdmip.filepath));
        if (cdmip.emBody != null && cdmip.emBody.files != null) {
            neuronEntity.setComputeFileData(
                    ComputeFileType.SkeletonSWC,
                    FileData.asFileFromString(cdmip.emBody.files.get("SkeletonSWC")));
            neuronEntity.setComputeFileData(
                    ComputeFileType.SkeletonOBJ,
                    FileData.asFileFromString(cdmip.emBody.files.get("SkeletonOBJ")));
        }
        return new InputCDMipNeuron<>(cdmip, neuronEntity);
    }

    private InputCDMipNeuron<ColorDepthMIP, LMNeuronEntity> asLMNeuron(ColorDepthMIP cdmip, Function<ColorDepthMIP, String> libraryNameExtractor) {
        String libraryName = libraryNameExtractor.apply(cdmip);
        LMNeuronEntity neuronEntity = new LMNeuronEntity();
        neuronEntity.setMipId(cdmip.id);
        neuronEntity.setAlignmentSpace(cdmip.alignmentSpace);
        neuronEntity.setLibraryName(libraryName);
        neuronEntity.setSourceRefId(cdmip.sampleRef);
        neuronEntity.setInternalLineName(cdmip.lmInternalLineName());
        neuronEntity.setPublishedName(cdmip.lmLineName());
        neuronEntity.setSlideCode(cdmip.lmSlideCode());
        neuronEntity.setAnatomicalArea(cdmip.anatomicalArea);
        neuronEntity.setGender(Gender.fromVal(cdmip.gender()));
        neuronEntity.setObjective(cdmip.objective);
        neuronEntity.setNotStaged(cdmip.lmIsNotStaged());
        neuronEntity.setPublishError(cdmip.lmPublishError());
        neuronEntity.addDatasetLabels(cdmip.lmReleaseNames());
        // set source color depth image
        neuronEntity.setComputeFileData(ComputeFileType.SourceColorDepthImage, FileData.asFileFromString(cdmip.filepath));
        return new InputCDMipNeuron<>(cdmip, neuronEntity);
    }

    private LibraryVariantArg getVariantForFileType(LibraryPathsArgs libraryPaths, ComputeFileType fileType) {
        return args.variantFileTypeMapping.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(fileType.name()))
                .map(Map.Entry::getKey)
                .map(vt -> libraryPaths.getLibraryVariant(vt).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(new LibraryVariantArg());
    }

    private <N extends AbstractNeuronEntity> void updateTag(N n) {
        n.addTag(args.tag);
    }

    private int countColorDepthMips(WebTarget serverEndpoint, String credentials, String alignmentSpace, String library,
                                    List<String> datasets, List<String> releases, List<String> mips) {
        WebTarget target = serverEndpoint.path("/data/colorDepthMIPsCount")
                .queryParam("libraryName", library)
                .queryParam("alignmentSpace", alignmentSpace)
                .queryParam("dataset", datasets != null ? datasets.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                .queryParam("release", releases != null ? releases.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                .queryParam("id", mips != null ? mips.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null);
        LOG.info("Count color depth mips using {}, l={}, as={}, ds={}, rs={}", target, library, alignmentSpace, datasets, releases);
        Response response = HttpHelper.createRequestWithCredentials(target.request(MediaType.TEXT_PLAIN), credentials).get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalStateException("Invalid response from " + target + " -> " + response);
        } else {
            return response.readEntity(Integer.class);
        }
    }

    private List<ColorDepthMIP> retrieveColorDepthMipsWithSamples(WebTarget serverEndpoint,
                                                                  String credentials,
                                                                  String alignmentSpace,
                                                                  String libraryName,
                                                                  List<String> datasets,
                                                                  List<String> releases,
                                                                  List<String> mips,
                                                                  int offset,
                                                                  int pageLength) {
        return retrieveColorDepthMips(
                serverEndpoint.path("/data/colorDepthMIPsWithSamples")
                        .queryParam("libraryName", libraryName)
                        .queryParam("alignmentSpace", alignmentSpace)
                        .queryParam("dataset", datasets != null ? datasets.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                        .queryParam("release", releases != null ? releases.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                        .queryParam("id", mips != null ? mips.stream().filter(StringUtils::isNotBlank).reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null)
                        .queryParam("offset", offset)
                        .queryParam("length", pageLength),
                credentials)
                ;
    }

    private List<ColorDepthMIP> retrieveColorDepthMips(WebTarget endpoint, String credentials) {
        LOG.info("Get mips from {}", endpoint);
        Response response = HttpHelper.createRequestWithCredentials(endpoint.request(MediaType.APPLICATION_JSON), credentials).get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalStateException("Invalid response from " + endpoint.getUri() + " -> " + response);
        } else {
            return response.readEntity(new GenericType<>(new TypeReference<List<ColorDepthMIP>>() {
            }.getType()));
        }
    }

    private void createColorDepthSearchInputDataFromLocalData(String libraryName,
                                                              List<LibraryVariantArg> libraryVariants,
                                                              Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedSourceMips,
                                                              Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedSearchableMips,
                                                              Map<String, List<MIPsHandlingUtils.MIPStoreEntry>> indexedJunkMips,
                                                              Set<String> includedPublishedNames,
                                                              Set<String> excludedNeurons,
                                                              CDMIPsWriter cdmipsWriter) {
        if (StringUtils.isBlank(libraryName)) {
            LOG.info("Source library path is empty");
            return; // nothing to do
        }
        LOG.info("Process {} indexed mip entries from {}", indexedSourceMips.size(), libraryName);
        if (CollectionUtils.isNotEmpty(includedPublishedNames)) {
            LOG.info("Include only {} published names", includedPublishedNames.size());
        }
        if (CollectionUtils.isNotEmpty(excludedNeurons)) {
            LOG.info("Exclude {} neurons", excludedNeurons.size());
        }
        Function<MIPsHandlingUtils.MIPStoreEntry, InputCDMipNeuron<String, ? extends AbstractNeuronEntity>> cdmipToNeuronEntity =
                MIPsHandlingUtils.isEmLibrary(libraryName)
                        ? cdmip -> asEMNeuronFromName(libraryName, cdmip.storeEntryType, cdmip.storeBasePath, cdmip.getEntryName())
                        : cdmip -> asLMNeuronFromName(libraryName, cdmip.storeEntryType, cdmip.storeBasePath, cdmip.getEntryName());

        List<AbstractNeuronEntity> cdNeurons = indexedSourceMips.entrySet().stream()
                .flatMap(indexedMipEntry -> indexedMipEntry.getValue().stream())
                .map(cdmipToNeuronEntity)
                .filter(cdmip -> CollectionUtils.isEmpty(includedPublishedNames) || CollectionUtils.containsAny(includedPublishedNames, cdmip.getNeuronMetadata().getPublishedName()))
                .filter(cdmip -> CollectionUtils.isEmpty(excludedNeurons) || !CollectionUtils.containsAny(excludedNeurons, cdmip.getNeuronMetadata().getNeuronId()))
                .flatMap(cdmip -> {
                    List<? extends AbstractNeuronEntity> searchableMIPs = MIPsHandlingUtils.lookupSearchableNeuronImages(
                            cdmip.getNeuronMetadata(),
                            null,
                            -1,
                            ComputeFileType.InputColorDepthImage,
                            indexedSearchableMips,
                            args.matchNeuronState,
                            args.segmentedImageChannelBase,
                            true);
                    List<? extends AbstractNeuronEntity> junkMIPs = MIPsHandlingUtils.lookupSearchableNeuronImages(
                            cdmip.getNeuronMetadata(),
                            null,
                            -1,
                            ComputeFileType.JunkImage,
                            indexedJunkMips,
                            args.matchNeuronState,
                            args.segmentedImageChannelBase,
                            true);
                    return Stream.concat(searchableMIPs.stream(), junkMIPs.stream())
                            .peek(n -> n.addTag(args.junkTag))
                            .map(n -> new InputCDMipNeuron<>(cdmip.getSourceMIP(), n));
                })
                .peek(cdmip -> populateOtherComputeFilesFromInput(
                        cdmip.getNeuronMetadata(),
                        EnumSet.of(ComputeFileType.GradientImage, ComputeFileType.ZGapImage, ComputeFileType.Vol3DSegmentation),
                        libraryVariants))
                .peek(cdmip -> this.updateTag(cdmip.getNeuronMetadata()))
                .map(InputCDMipNeuron::getNeuronMetadata)
                .collect(Collectors.toList());
        // write the mips
        cdmipsWriter.open();
        cdmipsWriter.write(cdNeurons);
        cdmipsWriter.close();
    }

    private InputCDMipNeuron<String, EMNeuronEntity> asEMNeuronFromName(String libraryName,
                                                                        FileData.FileDataType entryType,
                                                                        String libraryPath,
                                                                        String entryName) {
        EMNeuronEntity neuronEntity = new EMNeuronEntity();
        neuronEntity.setPublishedName(MIPsHandlingUtils.extractEMBodyIdFromName(entryName));
        neuronEntity.setLibraryName(libraryName);
        neuronEntity.setComputeFileData(
                ComputeFileType.SourceColorDepthImage,
                FileData.fromComponents(entryType, libraryPath, entryName));
        return new InputCDMipNeuron<>(entryName, neuronEntity);
    }

    private InputCDMipNeuron<String, LMNeuronEntity> asLMNeuronFromName(String libraryName,
                                                                        FileData.FileDataType entryType,
                                                                        String libraryPath,
                                                                        String entryName) {
        LMNeuronEntity neuronEntity = new LMNeuronEntity();
        neuronEntity.setLibraryName(libraryName);
        neuronEntity.setComputeFileData(
                ComputeFileType.SourceColorDepthImage,
                FileData.fromComponents(entryType, libraryPath, entryName));
        populateLMDataFromFileName(entryName, neuronEntity);
        return new InputCDMipNeuron<>(entryName, neuronEntity);
    }

    /**
     * This is kludgy but we haven't needed it so far. It is here just for completeness.
     *
     * @param fn
     * @param neuronEntity
     */
    void populateLMDataFromFileName(String fn, LMNeuronEntity neuronEntity) {
        String[] fnComponents = StringUtils.split(fn, '-');
        String line = fnComponents.length > 0 ? fnComponents[0] : fn;
        // internal name can have PI initials
        neuronEntity.setInternalLineName(line);
        // attempt to remove the PI initials
        int piSeparator = StringUtils.indexOf(line, '_');
        String lineID;
        if (piSeparator == -1) {
            lineID = line;
        } else {
            lineID = line.substring(piSeparator + 1);
        }
        neuronEntity.setPublishedName(StringUtils.defaultIfBlank(lineID, "Unknown"));
    }
}
