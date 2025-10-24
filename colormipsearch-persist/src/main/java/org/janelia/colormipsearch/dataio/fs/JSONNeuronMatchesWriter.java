package org.janelia.colormipsearch.dataio.fs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.dataio.fileutils.ItemsWriterToJSONFile;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EntityField;
import org.janelia.colormipsearch.results.GroupedMatchedEntities;
import org.janelia.colormipsearch.results.MatchEntitiesGrouping;

public class JSONNeuronMatchesWriter<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, R extends AbstractMatchEntity<M, T>>
        implements NeuronMatchesWriter<R> {

    private final ItemsWriterToJSONFile resultMatchesWriter;
    // results grouping is used both for grouping the matches and for getting the filename
    private final Function<AbstractNeuronEntity, String> resultsGrouping;
    private final Comparator<AbstractMatchEntity<?, ?>> matchOrdering;
    private final Path perMasksOutputDir;
    private final Path perMatchesOutputDir;

    public JSONNeuronMatchesWriter(ObjectWriter jsonWriter,
                                   Function<AbstractNeuronEntity, String> resultsGrouping,
                                   Comparator<AbstractMatchEntity<?, ?>> matchOrdering,
                                   Path perMasksOutputDir,
                                   Path perMatchesOutputDir) {
        this.resultMatchesWriter = new ItemsWriterToJSONFile(jsonWriter);
        this.resultsGrouping = resultsGrouping;
        this.matchOrdering = matchOrdering;
        this.perMatchesOutputDir = perMatchesOutputDir;
        this.perMasksOutputDir = perMasksOutputDir;
    }

    @Override
    public long write(List<R> matches) {
        long nresults = 0;
        if (perMasksOutputDir != null) {
            nresults += writeMatchesByMask(matches);
        }
        if (perMatchesOutputDir != null) {
            nresults += writeMatchesByTarget(matches);
        }
        return nresults;
    }

    @Override
    public long writeUpdates(List<R> matches, List<Function<R, Pair<String, ?>>> fieldSelectors) {
        return writeMatchesByMask(matches);
    }

    private int writeMatchesByMask(List<R> matches) {
        // write results by mask ID
        Function<M, String> grouping = resultsGrouping::apply;
        Comparator<R> ordering = matchOrdering::compare;
        List<GroupedMatchedEntities<M, T, R>> resultMatches = MatchEntitiesGrouping.groupByMaskFields(
                matches,
                Collections.singletonList(grouping),
                m -> m.getMatchedImage() != null,
                ordering
        );
        resultMatchesWriter.writeGroupedItemsList(resultMatches, grouping, perMasksOutputDir);
        return resultMatches.size();
    }

    private int writeMatchesByTarget(List<R> matches) {
        // write results by matched ID
        Function<T, String> grouping = resultsGrouping::apply;
        Comparator<AbstractMatchEntity<T, M>> ordering = matchOrdering::compare;
        List<GroupedMatchedEntities<T, M, AbstractMatchEntity<T, M>>> resultMatches = MatchEntitiesGrouping.groupByTargetFields(
                matches,
                Collections.singletonList(grouping),
                m -> m.getMatchedImage() != null,
                ordering
        );
        resultMatchesWriter.writeGroupedItemsList(resultMatches, grouping, perMatchesOutputDir);
        return resultMatches.size();
    }

    @Override
    public long bulkWriteUpdates(List<R> matches, List<EntityField<?>> fieldUpdates) {
        throw new UnsupportedOperationException();
    }
}
