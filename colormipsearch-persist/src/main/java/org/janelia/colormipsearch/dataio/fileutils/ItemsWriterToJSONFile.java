package org.janelia.colormipsearch.dataio.fileutils;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.dataio.fs.JsonOutputHelper;
import org.janelia.colormipsearch.model.GroupedItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemsWriterToJSONFile {
    private static final Logger LOG = LoggerFactory.getLogger(ItemsWriterToJSONFile.class);

    private final ObjectWriter jsonWriter;

    public ItemsWriterToJSONFile(ObjectWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public <R> void writeJSON(R result, Path outputDir, String filename) {
        FSUtils.createDirs(outputDir);
        JsonOutputHelper.writeToJSONFile(
                result,
                FSUtils.getOutputPath(outputDir, getJsonFile(filename)),
                jsonWriter);
    }

    public <M, R> void writeGroupedItemsList(List<? extends GroupedItems<M, R>> groupedItemsList,
                                             Function<M, String> filenameSelector,
                                             Path outputDir) {
        long startTime = System.currentTimeMillis();
        FSUtils.createDirs(outputDir);
        LOG.info("Write {} file results to {}", groupedItemsList.size(), outputDir);
        groupedItemsList.stream().parallel()
                .forEach(resultMatches -> writeGroupedItems(resultMatches, filenameSelector, outputDir));
        LOG.info("Finished writing {} file results in {}s", groupedItemsList.size(), (System.currentTimeMillis() - startTime) / 1000.);
    }

    private <M, R> void writeGroupedItems(GroupedItems<M, R> groupedItems,
                                          Function<M, String> filenameSelector,
                                          Path outputDir) {
        String filename = filenameSelector.apply(groupedItems.getKey());
        JsonOutputHelper.writeToJSONFile(
                groupedItems,
                FSUtils.getOutputPath(outputDir, getJsonFile(filename)),
                jsonWriter);
    }

    private File getJsonFile(String fn) {
        if (StringUtils.isBlank(fn)) {
            return null;
        } else {
            return new File(fn + ".json");
        }
    }
}