package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.client.model.Filters;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.datarequests.ScoresFilter;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;

class NeuronSelectionHelper {

    private static final Document NO_FILTER = new Document();

    static Bson getNeuronFilter(String fieldQualifier, NeuronSelector neuronSelector) {
        if (neuronSelector == null || neuronSelector.isEmpty()) {
            return NO_FILTER;
        }
        String qualifier = StringUtils.isNotBlank(fieldQualifier) ? fieldQualifier + "." : "";

        List<Bson> filter = new ArrayList<>();
        if (neuronSelector.hasEntityIds()) {
            filter.add(Filters.in(qualifier + "_id", neuronSelector.getEntityIds()));
        }
        if (neuronSelector.hasNeuronClassname()) {
            filter.add(Filters.eq(qualifier + "class", neuronSelector.getNeuronClassname()));
        }
        if (neuronSelector.hasAlignmentSpace()) {
            filter.add(Filters.eq(qualifier + "alignmentSpace", neuronSelector.getAlignmentSpace()));
        }
        if (neuronSelector.hasLibraries()) {
            filter.add(Filters.in(qualifier + "libraryName", neuronSelector.getLibraries()));
        }
        if (neuronSelector.isCheckIfNameValid()) {
            filter.add(Filters.and(
                    Filters.exists(qualifier + "publishedName"),
                    Filters.ne(qualifier + "publishedName", AbstractNeuronEntity.NO_CONSENSUS))
            );
        }
        if (neuronSelector.hasNames()) {
            filter.add(Filters.in(qualifier + "publishedName", neuronSelector.getNames()));
        }
        if (neuronSelector.hasMipIDs()) {
            filter.add(Filters.in(qualifier + "mipId", neuronSelector.getMipIDs()));
        }
        if (neuronSelector.hasSourceRefIds()) {
            filter.add(Filters.in(qualifier + "sourceRefId", neuronSelector.getSourceRefIds()));
        }
        if (neuronSelector.hasDatasetLabels()) {
            filter.add(Filters.in(qualifier + "datasetLabels", neuronSelector.getDatasetLabels()));
        }
        if (neuronSelector.hasTags()) {
            filter.add(Filters.in(qualifier + "tags", neuronSelector.getTags()));
        }
        if (neuronSelector.hasExcludedTags()) {
            filter.add(Filters.nin(qualifier + "tags", neuronSelector.getExcludedTags()));
        }
        if (neuronSelector.hasAnnotations()) {
            filter.add(Filters.in(qualifier + "neuronTerms", neuronSelector.getAnnotations()));
        }
        if (neuronSelector.hasExcludedAnnotations()) {
            filter.add(Filters.nin(qualifier + "neuronTerms", neuronSelector.getExcludedAnnotations()));
        }
        if (neuronSelector.hasProcessedTags()) {
            // all filters from a selection are "and"-ed
            // and all selections are "or"-ed
            filter.add(
                    Filters.or(
                            neuronSelector.getProcessedTagsSelections().stream()
                                    .map(processedTagsSelection -> Filters.and(
                                            processedTagsSelection.entrySet().stream()
                                                    .map(pte -> Filters.in(qualifier + "processedTags" + "." + pte.getKey(), pte.getValue()))
                                                    .collect(Collectors.toList())
                                    ))
                                    .collect(Collectors.toList())
                    )
            );
        }
        if (filter.isEmpty()) {
            return NO_FILTER;
        } else if (filter.size() == 1) {
            return filter.get(0);
        } else {
            return Filters.and(filter);
        }
    }

    static <R extends AbstractMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>>
    Bson getNeuronsMatchFilter(NeuronsMatchFilter<R> neuronsMatchFilter) {
        List<Bson> filter = new ArrayList<>();
        if (neuronsMatchFilter != null) {
            if (neuronsMatchFilter.getMatchEntityType() != null) {
                filter.add(MongoDaoHelper.createFilterByClass(neuronsMatchFilter.getMatchEntityType()));
            }
            addInFilter("_id", neuronsMatchFilter.getMatchEntityIds(), filter);
            addNeuronsMatchScoresFilters(neuronsMatchFilter.getScoresFilter(), filter);
            addInFilter("maskImageRefId", neuronsMatchFilter.getMaskEntityIds(), filter);
            addInFilter("matchedImageRefId", neuronsMatchFilter.getTargetEntityIds(), filter);
            addInFilter("tags", neuronsMatchFilter.getTags(), filter);
            if (neuronsMatchFilter.hasExcludedTags()) {
                filter.add(Filters.nin("tags", neuronsMatchFilter.getExcludedTags()));
            }
        }
        return MongoDaoHelper.createBsonFilterCriteria(filter);
    }

    private static void addNeuronsMatchScoresFilters(ScoresFilter neuronsMatchScoresFilter, List<Bson> filter) {
        if ((neuronsMatchScoresFilter == null || neuronsMatchScoresFilter.isEmpty())) {
            return;
        }
        neuronsMatchScoresFilter.getScoreSelectors().forEach(s -> filter.add(Filters.gte(s.getFieldName(), s.getMinScore())));
    }

    private static <E> void addInFilter(String attrName, Collection<E> values, List<Bson> filter) {
        if (CollectionUtils.isNotEmpty(values)) {
            filter.add(MongoDaoHelper.createInFilter(attrName, values));
        }
    }
}
