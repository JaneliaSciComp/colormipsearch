package org.janelia.colormipsearch.cmd.dataexport;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.cmd.jacsdata.CachedDataHelper;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.CDMatchedTarget;
import org.janelia.colormipsearch.dto.ResultMatches;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.FileType;
import org.janelia.colormipsearch.model.PublishedURLs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCDMatchesExporter extends AbstractDataExporter {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCDMatchesExporter.class);
    private static final Pattern SUSPICIOUS_MATCH_PATTERN = Pattern.compile("Suspicious match from .+ import");
    private static final String DEFAULT_SEARCHABLE_NEURON_PATH = "%s/%s/searchable_neurons/pngs/%s";
    final ScoresFilter scoresFilter;
    final NeuronMatchesReader<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader;
    final ItemsWriterToJSONFile resultMatchesWriter;
    final int processingPartitionSize;

    protected AbstractCDMatchesExporter(CachedDataHelper jacsDataHelper,
                                        DataSourceParam dataSourceParam,
                                        ScoresFilter scoresFilter,
                                        int relativesUrlsToComponent,
                                        ImageStoreMapping imageStoreMapping,
                                        Path outputDir,
                                        Executor executor,
                                        NeuronMatchesReader<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader,
                                        ItemsWriterToJSONFile resultMatchesWriter,
                                        int processingPartitionSize) {
        super(jacsDataHelper, dataSourceParam, relativesUrlsToComponent, imageStoreMapping, outputDir, executor);
        this.scoresFilter = scoresFilter;
        this.neuronMatchesReader = neuronMatchesReader;
        this.resultMatchesWriter = resultMatchesWriter;
        this.processingPartitionSize = processingPartitionSize;
    }

    void retrieveAllCDMIPs(List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> matches) {
        // retrieve source ColorDepth MIPs
        Set<String> sourceMIPIds = matches.stream()
                .flatMap(m -> Stream.of(m.getMaskMIPId(), m.getMatchedMIPId()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        dataHelper.cacheCDMIPs(sourceMIPIds);
    }

    /**
     * Select the best matches for each pair of mip IDs
     */
    <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity> List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> selectBestMatchPerMIPPair(
            List<CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> cdMatchEntities) {
        Map<Pair<String, String>, CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> bestMatchesPerMIPsPairs = cdMatchEntities.stream()
                .filter(this::doesNotLookSuspicious)
                .collect(Collectors.toMap(
                        m -> ImmutablePair.of(m.getMaskMIPId(), m.getMatchedMIPId()),
                        m -> m,
                        (m1, m2) -> m1.getNormalizedScore() >= m2.getNormalizedScore() ? m1 : m2)); // resolve by picking the best match
        return new ArrayList<>(bestMatchesPerMIPsPairs.values());
    }

    /**
     * Check if the match was marked as suspicious at the time of import. That happens if one of the neurons,
     * either the mask or the target, did not exist at the time of import and it was artificially created.
     *
     * @param m
     * @return
     */
    private boolean doesNotLookSuspicious(CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity> m) {
        return m.getTags().stream().noneMatch(t -> SUSPICIOUS_MATCH_PATTERN.matcher(t).find());
    }

    <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> void
    updateMatchedResultsMetadata(ResultMatches<M, CDMatchedTarget<T>> resultMatches,
                                 BiConsumer<M, PublishedURLs> updateKeyMethod,
                                 BiConsumer<T, PublishedURLs> updateTargetMatchMethod,
                                 Map<Number, PublishedURLs> publisheURLsByNeuronId) {
        updateKeyMethod.accept(resultMatches.getKey(), publisheURLsByNeuronId.get(resultMatches.getKey().getInternalId()));
        resultMatches.getKey().transformAllNeuronFiles(this::relativizeURL);
        String maskImageStore = resultMatches.getKey().getNeuronFile(FileType.store);
        String maskSourceImageName = resultMatches.getKey().getNeuronComputeFile(ComputeFileType.SourceColorDepthImage);
        String maskMipImageName = resultMatches.getKey().getNeuronFile(FileType.CDM);
        resultMatches.getItems().forEach(target -> {
            updateTargetMatchMethod.accept(target.getTargetImage(), publisheURLsByNeuronId.get(target.getTargetImage().getInternalId()));
            target.getTargetImage().transformAllNeuronFiles(this::relativizeURL);
            // update match files - ideally we get these from PublishedURLs but
            // if they are not there we try to create the searchable URL based on the input name and ColorDepthMIP name
            PublishedURLs maskPublishedURLs = publisheURLsByNeuronId.get(target.getMaskImageInternalId());
            String maskImageURL = getSearchableNeuronURL(maskPublishedURLs);
            if (maskImageURL == null) {
                LOG.error("No published URLs or no searchable neuron URL for match mask {}:{} -> {}", target.getMaskImageInternalId(), resultMatches.getKey(), target);
                String maskInputImageName = target.getMatchFile(FileType.CDMInput);
                String imageName = getMIPFileName(maskInputImageName, maskSourceImageName, maskMipImageName);
                String searchableNeuronPath = String.format(DEFAULT_SEARCHABLE_NEURON_PATH,
                        resultMatches.getKey().getAlignmentSpace(),
                        resultMatches.getKey().getLibraryName(),
                        imageName);
                if (StringUtils.isNotBlank(searchableNeuronPath)) {
                    LOG.warn("Use default - {} - searchable neuron set for match mask {}:{} -> {}", searchableNeuronPath, target.getMaskImageInternalId(), resultMatches.getKey(), target);
                    target.setMatchFile(FileType.CDMInput, searchableNeuronPath);
                } else {
                    LOG.error("No default searchable neuron set for match mask {}:{} -> {}", target.getMaskImageInternalId(), resultMatches.getKey(), target);
                }
            } else {
                target.setMatchFile(FileType.CDMInput, relativizeURL(maskImageURL));
            }
            PublishedURLs targetPublishedURLs = publisheURLsByNeuronId.get(target.getTargetImage().getInternalId());
            String targetImageStore = target.getTargetImage().getNeuronFile(FileType.store);
            String tagetImageURL = getSearchableNeuronURL(targetPublishedURLs);
            if (tagetImageURL == null) {
                LOG.error("No published URLs or no searchable neuron URL for match target {}:{} -> {}", target.getMaskImageInternalId(), resultMatches.getKey(), target);
                String targetInputImageName = target.getMatchFile(FileType.CDMMatch);
                String targetSourceImageName = target.getTargetImage().getNeuronComputeFile(ComputeFileType.SourceColorDepthImage);
                String targetMipImageName = target.getTargetImage().getNeuronFile(FileType.CDM);
                String imageName = getMIPFileName(targetInputImageName, targetSourceImageName, targetMipImageName);
                if (StringUtils.isNotBlank(imageName)) {
                    String searchableNeuronPath = String.format(DEFAULT_SEARCHABLE_NEURON_PATH,
                            target.getTargetImage().getAlignmentSpace(),
                            target.getTargetImage().getLibraryName(),
                            imageName);
                    LOG.warn("Use default - {} - searchable neuron set for match target {}:{} -> {}", searchableNeuronPath, target.getMaskImageInternalId(), resultMatches.getKey(), target);
                    target.setMatchFile(FileType.CDMMatch, searchableNeuronPath);
                } else {
                    LOG.error("No default searchable neuron set for match target {}:{} -> {}", target.getMaskImageInternalId(), resultMatches.getKey(), target);
                }
            } else {
                target.setMatchFile(FileType.CDMMatch, relativizeURL(tagetImageURL));
            }
            if (!StringUtils.equals(maskImageStore, targetImageStore)) {
                LOG.error("Image stores for mask {} and target {} do not match - this will become a problem when viewing this match",
                        maskImageStore, targetImageStore);
            } else {
                target.setMatchFile(FileType.store, targetImageStore);
            }
        });
    }

    private String getSearchableNeuronURL(PublishedURLs publishedURLs) {
        if (publishedURLs != null) {
            return publishedURLs.getURLFor("searchable_neurons", null);
        } else {
            return null;
        }
    }

    /**
     * This creates the corresponding display name for the input MIP.
     * To do that it finds the suffix that was used to create the inputImageName from the sourceImageName and appends it to the mipImageName.
     *
     * @param inputImageFileName
     * @param sourceImageFileName
     * @param mipImageFileName
     * @return
     */
    private String getMIPFileName(String inputImageFileName, String sourceImageFileName, String mipImageFileName) {
        if (StringUtils.isNotBlank(mipImageFileName) &&
                StringUtils.isNotBlank(sourceImageFileName) &&
                StringUtils.isNotBlank(inputImageFileName)) {
            String sourceName = RegExUtils.replacePattern(
                    new File(sourceImageFileName).getName(),
                    "(_)?(CDM)?\\..*$", ""); // clear  _CDM.<ext> suffix
            String inputName = RegExUtils.replacePattern(
                    new File(inputImageFileName).getName(),
                    "(_)?(CDM)?\\..*$", ""); // clear  _CDM.<ext> suffix
            String imageSuffix = RegExUtils.replacePattern(
                    StringUtils.removeStart(inputName, sourceName), // typically the segmentation name shares the same prefix with the original mip name
                    "^[-_]",
                    ""
            ); // remove the hyphen or underscore prefix
            String mipName = RegExUtils.replacePattern(new File(mipImageFileName).getName(),
                    "\\..*$", ""); // clear  .<ext> suffix
            return StringUtils.isBlank(imageSuffix)
                    ? mipName + ".png"
                    : mipName + "-" + imageSuffix + ".png";
        } else {
            return null;
        }
    }
}
