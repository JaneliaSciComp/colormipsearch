package org.janelia.colormipsearch.cmd.dataexport;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.janelia.colormipsearch.cmd.jacsdata.CachedJacsDataHelper;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.NeuronMatchesReader;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.datarequests.SortDirection;
import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.PPPMatchedTarget;
import org.janelia.colormipsearch.dto.ResultMatches;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.janelia.colormipsearch.results.MatchResultsGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PPPMatchesExporter extends AbstractDataExporter {
    private static final Logger LOG = LoggerFactory.getLogger(PerMaskCDMatchesExporter.class);

    private final ScoresFilter scoresFilter;
    private final NeuronMatchesReader<PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader;
    private final ItemsWriterToJSONFile resultMatchesWriter;
    private final int processingPartitionSize;

    public PPPMatchesExporter(CachedJacsDataHelper jacsDataHelper,
                              DataSourceParam dataSourceParam,
                              ScoresFilter scoresFilter,
                              Path outputDir,
                              NeuronMatchesReader<PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> neuronMatchesReader,
                              ItemsWriterToJSONFile resultMatchesWriter,
                              int processingPartitionSize) {
        super(jacsDataHelper, dataSourceParam, outputDir);
        this.scoresFilter = scoresFilter;
        this.neuronMatchesReader = neuronMatchesReader;
        this.resultMatchesWriter = resultMatchesWriter;
        this.processingPartitionSize = processingPartitionSize;
    }

    @Override
    public DataSourceParam getDataSource() {
        return dataSourceParam;
    }

    @Override
    public void runExport() {
        List<String> masks = neuronMatchesReader.listMatchesLocations(Collections.singletonList(dataSourceParam));
        ItemsHandling.partitionCollection(masks, processingPartitionSize).entrySet().stream().parallel()
                .forEach(indexedPartition -> {
                    indexedPartition.getValue().forEach(maskId -> {
                        LOG.info("Read PPP matches for {}", maskId);
                        List<PPPMatchEntity<?, ?>> matchesForMask = neuronMatchesReader.readMatchesForMasks(
                                dataSourceParam.getAlignmentSpace(),
                                dataSourceParam.getLibraryName(),
                                Collections.singletonList(maskId),
                                scoresFilter,
                                null, // use the tags for selecting the masks but not for selecting the matches
                                Collections.singletonList(
                                        new SortCriteria("rank", SortDirection.ASC)
                                ));
                        LOG.info("Write PPP matches for {}", maskId);
                        writeResults(matchesForMask);
                    });
                });
    }

    private <M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> void
    writeResults(List<PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> matches) {
        // group results by mask
        List<Function<M, ?>> grouping = Collections.singletonList(
                AbstractNeuronMetadata::getPublishedName
        );
        // order ascending by rank
        Comparator<PPPMatchedTarget<T>> ordering = Comparator.comparingDouble(PPPMatchedTarget::getRank);
        List<ResultMatches<M, PPPMatchedTarget<T>>> matchesByMask = MatchResultsGrouping.groupByMask(
                matches,
                grouping,
                ordering);
        // write results by mask (EM) published name
        resultMatchesWriter.writeGroupedItemsList(matchesByMask, AbstractNeuronMetadata::getPublishedName, outputDir);
    }
}
