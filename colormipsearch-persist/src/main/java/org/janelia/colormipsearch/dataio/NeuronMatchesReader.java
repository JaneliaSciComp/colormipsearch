package org.janelia.colormipsearch.dataio;

import java.util.Collection;
import java.util.List;

import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

public interface NeuronMatchesReader<R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> {
    /**
     * This method will list the location for all color depth matches.
     *
     * A file based implementation will return all files that contain CD matches.
     * A database based implementation will return  all distinct MIP IDs from a library.
     *
     * @param matchesSource
     * @return
     */
    List<String> listMatchesLocations(Collection<DataSourceParam> matchesSource);

    /**
     * Read matches for the specified masks and targets - iterate by mask first. A null or empty list will ignore that filter.
     *
     * @param alignmentSpace
     * @param maskDataSource
     * @param targetDataSource
     * @param matchTags
     * @param matchExcludedTags
     * @param matchScoresFilter
     * @param sortCriteriaList
     * @return
     */
    List<R> readMatchesByMask(String alignmentSpace,
                              DataSourceParam maskDataSource,
                              DataSourceParam targetDataSource,
                              Collection<String> matchTags,
                              Collection<String> matchExcludedTags,
                              ScoresFilter matchScoresFilter,
                              List<SortCriteria> sortCriteriaList);

    /**
     * Read matches for the specified masks and targets - iterate by target first. A null or empty list will ignore that filter.
     *
     * @param alignmentSpace
     * @param maskDataSource
     * @param targetDataSource
     * @param matchTags
     * @param matchExcludedTags
     * @param matchScoresFilter
     * @param sortCriteriaList
     * @return
     */
    List<R> readMatchesByTarget(String alignmentSpace,
                                DataSourceParam maskDataSource,
                                DataSourceParam targetDataSource,
                                Collection<String> matchTags,
                                Collection<String> matchExcludedTags,
                                ScoresFilter matchScoresFilter,
                                List<SortCriteria> sortCriteriaList);

}
