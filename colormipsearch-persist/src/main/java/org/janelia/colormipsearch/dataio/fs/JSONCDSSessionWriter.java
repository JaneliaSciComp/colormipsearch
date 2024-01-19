package org.janelia.colormipsearch.dataio.fs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.dataio.CDSSessionWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONCDSSessionWriter implements CDSSessionWriter {
    private static final Logger LOG = LoggerFactory.getLogger(JSONCDSSessionWriter.class);

    private final Path outputDir;
    private final ObjectMapper mapper;

    public JSONCDSSessionWriter(Path outputDir, ObjectMapper mapper) {
        this.outputDir = outputDir;
        this.mapper = mapper;
    }

    @Override
    public Number createSession(List<DataSourceParam> masksInputs,
                                List<DataSourceParam> targetsInputs,
                                Map<String, Object> params,
                                Set<String> tags) {
        String masksInputValues = inputValues(masksInputs);
        String targetsInputValues = inputValues(targetsInputs);
        File outputFile;
        if (outputDir != null) {
            FSUtils.createDirs(outputDir);
            outputFile = outputDir.resolve("masks-" + masksInputValues + "-targets-" + targetsInputValues + "-cdsParameters.json").toFile();
        } else {
            outputFile = null;
        }
        try {
            if (outputFile != null) {
                mapper.writerWithDefaultPrettyPrinter().
                        writeValue(outputFile, params);
            } else {
                mapper.writerWithDefaultPrettyPrinter().
                        writeValue(System.out, params);
            }
        } catch (IOException e) {
            LOG.error("Error persisting color depth search parameters to {}", outputFile, e);
            throw new IllegalStateException(e);
        }
        return System.currentTimeMillis(); // use the current timestamp
    }

    private String inputValues(List<DataSourceParam> inputs) {
        return inputs.stream()
                .flatMap(input -> input.getLibraries().stream().map(Files::getNameWithoutExtension))
                .reduce("", (l1, l2) -> StringUtils.isBlank(l1) ? l2 : l1 + "-" + l2);
    }

}
