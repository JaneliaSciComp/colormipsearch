package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.model.Filters;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.model.AbstractMatch;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;

class NeuronSelectionHelper {

    private static final Document NO_FILTER = new Document();

    static Bson getNeuronFilter(String fieldQualifier, NeuronSelector neuronSelector) {
        if (neuronSelector == null || neuronSelector.isEmpty()) {
            return NO_FILTER;
        }
        String qualifier = StringUtils.isNotBlank(fieldQualifier) ? fieldQualifier + "." : "";

        List<Bson> filter = new ArrayList<>();
        if (neuronSelector.hasNeuronClassname()) {
            filter.add(Filters.eq(qualifier + "class", neuronSelector.getNeuronClassname()));
        }
        if (neuronSelector.hasLibraryName()) {
            filter.add(Filters.eq(qualifier + "libraryName", neuronSelector.getLibraryName()));
        }
        if (neuronSelector.hasNames()) {
            filter.add(Filters.in(qualifier + "publishedName", neuronSelector.getNames()));
        }
        if (neuronSelector.hasMipIDs()) {
            filter.add(Filters.in(qualifier + "id", neuronSelector.getMipIDs()));
        }
        if (filter.isEmpty()) {
            return NO_FILTER;
        } else if (filter.size() == 1) {
            return filter.get(0);
        } else {
            return Filters.and(filter);
        }
    }

    static <R extends AbstractMatch<? extends AbstractNeuronMetadata, ? extends AbstractNeuronMetadata>>
    Bson getNeuronsMatchFilter(NeuronsMatchFilter<R> neuronsMatchFilter, NeuronSelector maskSelector, NeuronSelector targetSelector) {
        List<Bson> filter = new ArrayList<>();
        addNeuronMatchesFilters(neuronsMatchFilter, filter);
        if (maskSelector != null && maskSelector.hasEntityIds()) {
            filter.add(MongoDaoHelper.createInFilter("maskImageRefId", maskSelector.getEntityIds()));
        }
        if (targetSelector != null && targetSelector.hasEntityIds()) {
            filter.add(MongoDaoHelper.createInFilter("matchedImageRefId", targetSelector.getEntityIds()));
        }
        return MongoDaoHelper.createBsonFilterCriteria(filter);
    }

    static private <R extends AbstractMatch<? extends AbstractNeuronMetadata, ? extends AbstractNeuronMetadata>>
    void addNeuronMatchesFilters(NeuronsMatchFilter<R> neuronsMatchFilter, List<Bson> filter) {
        if ((neuronsMatchFilter == null || neuronsMatchFilter.isEmpty())) {
            return;
        }
        if (neuronsMatchFilter.hasMatchType()) {
            filter.add(MongoDaoHelper.createFilterByClass(neuronsMatchFilter.getMatchType()));
        }
        neuronsMatchFilter.getScoreSelectors().forEach(s -> filter.add(Filters.gte(s.getFieldName(), s.getMinScore())));
    }

}
