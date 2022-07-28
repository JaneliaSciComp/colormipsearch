package org.janelia.colormipsearch.dataio.fs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dataio.NeuronMatchesWriter;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.results.MatchResultsGrouping;
import org.janelia.colormipsearch.results.ResultMatches;

public class JSONNeuronMatchesWriter<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, R extends AbstractMatchEntity<M, T>>
        implements NeuronMatchesWriter<R> {

    private final JSONResultMatchesWriter resultMatchesWriter;
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
        this.resultMatchesWriter = new JSONResultMatchesWriter(jsonWriter);
        this.resultsGrouping = resultsGrouping;
        this.matchOrdering = matchOrdering;
        this.perMatchesOutputDir = perMatchesOutputDir;
        this.perMasksOutputDir = perMasksOutputDir;
    }

    @Override
    public void write(List<R> matches) {
        if (perMasksOutputDir != null) {
            writeMatchesByMask(matches);
        }
        if (perMatchesOutputDir != null) {
            writeMatchesByTarget(matches);
        }
    }

    @Override
    public void writeUpdates(List<R> matches, List<Function<R, Pair<String, ?>>> fieldSelectors) {
        writeMatchesByMask(matches);
    }

    private void writeMatchesByMask(List<R> matches) {
        // write results by mask ID
        List<Function<M, ?>> grouping = Collections.singletonList(
                resultsGrouping::apply
        );
        Comparator<R> ordering = matchOrdering::compare;
        List<ResultMatches<M, T, R>> resultMatches = MatchResultsGrouping.groupByMaskFields(
                matches,
                grouping,
                ordering
        );
        resultMatchesWriter.writeResultMatchesList(resultMatches, resultsGrouping, perMasksOutputDir);
    }

    private void writeMatchesByTarget(List<R> matches) {
        // write results by matched ID
        List<Function<T, ?>> grouping = Collections.singletonList(
                resultsGrouping::apply
        );
        Comparator<AbstractMatchEntity<T, M>> ordering = matchOrdering::compare;
        List<ResultMatches<T, M, AbstractMatchEntity<T, M>>> resultMatches = MatchResultsGrouping.groupByTargetFields(
                matches,
                grouping,
                ordering
        );
        resultMatchesWriter.writeResultMatchesList(resultMatches, resultsGrouping, perMatchesOutputDir);
    }
}
