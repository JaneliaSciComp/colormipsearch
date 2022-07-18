package org.janelia.colormipsearch.dao.mongo.support;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.model.Filters;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.NeuronSelector;

public class NeuronSelectionHelper {

    public static final Document NO_FILTER = new Document();

    public static Bson getNeuronMatchFilter(String fieldQualifier, NeuronSelector neuronSelector) {
        if (neuronSelector == null || neuronSelector.isEmpty()) {
            return NO_FILTER;
        }
        String qualifier = StringUtils.isNotBlank(fieldQualifier) ? fieldQualifier + "." : "";

        List<Bson> filter = new ArrayList<>();
        if (neuronSelector.hasLibraryName()) {
            filter.add(Filters.eq(qualifier + "libraryName", neuronSelector.getLibraryName()));
        }
        if (neuronSelector.hasNames()) {
            filter.add(Filters.in(qualifier + "publishedName", neuronSelector.getNames()));
        }
        if (neuronSelector.hasMipIDs()) {
            filter.add(Filters.in(qualifier + "id", neuronSelector.getMipIDs()));
        }
        return filter.isEmpty() ? NO_FILTER : Filters.and(filter);
    }

}
