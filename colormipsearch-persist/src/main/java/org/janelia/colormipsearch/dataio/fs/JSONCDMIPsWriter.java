package org.janelia.colormipsearch.dataio.fs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.janelia.colormipsearch.dataio.CDMIPsWriter;
import org.janelia.colormipsearch.dataio.fileutils.FSUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONCDMIPsWriter implements CDMIPsWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JSONCDMIPsWriter.class);

    private final ObjectMapper mapper;
    private final Path outputPath;
    private final File outputFile;
    private final boolean append;
    private JsonGenerator gen;

    public JSONCDMIPsWriter(Path outputPath,
                            String outputFileName,
                            int libraryFromIndex,
                            int libraryToIndex,
                            boolean append,
                            ObjectMapper mapper) {
        String outputName = libraryFromIndex > 0
                ? outputFileName + "-" + libraryFromIndex + "-" + libraryToIndex + ".json"
                : outputFileName + ".json";
        this.outputPath = outputPath;
        this.outputFile = outputPath.resolve(outputName).toFile();
        this.append = append;
        this.mapper = mapper;
    }

    @Override
    public void open() {
        FSUtils.createDirs(outputPath);
        this.gen = createJsonGenerator();
    }

    @Override
    public void write(List<? extends AbstractNeuronEntity> neuronEntities) {
        neuronEntities.forEach(this::writeOne);
    }

    @Override
    public void writeOne(AbstractNeuronEntity neuronEntity) {
        try {
            gen.writeObject(neuronEntity);
        } catch (Exception e) {
            LOG.error("Error writing {}", neuronEntity, e);
        }
    }

    @Override
    public void addProcessingTags(Collection<? extends AbstractNeuronEntity> neuronEntities, ProcessingType processingType, Set<String> tags) {
        // do nothing here
    }

    @Override
    public void close() {
        try {
            gen.writeEndArray();
            gen.flush();
        } catch (Exception e) {
            LOG.error("Error finalizing CDS data generator for {}", gen.getOutputTarget(), e);
        }
    }

    private JsonGenerator createJsonGenerator() {
        LOG.info("Write color depth MIPs to {}", outputFile);
        if (outputFile.exists() && append) {
            return openOutputForAppend();
        } else {
            return openOutput();
        }
    }

    private JsonGenerator openOutputForAppend() {
        try {
            LOG.debug("Append to {}", outputFile);
            RandomAccessFile rf = new RandomAccessFile(outputFile, "rw");
            long rfLength = rf.length();
            // position FP after the end of the last item
            // this may not work on Windows because of the new line separator
            // - so on windows it may need to rollback more than 4 chars
            rf.seek(rfLength - 2);
            OutputStream outputStream = Channels.newOutputStream(rf.getChannel());
            outputStream.write(',');
            long pos = rf.getFilePointer();
            JsonGenerator gen = mapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8);
            gen.useDefaultPrettyPrinter();
            gen.writeStartArray();
            gen.flush();
            rf.seek(pos);
            return gen;
        } catch (IOException e) {
            LOG.error("Error creating the output stream to be appended for {}", outputFile, e);
            throw new UncheckedIOException(e);
        }
    }

    private JsonGenerator openOutput() {
        try {
            JsonGenerator gen = mapper.getFactory().createGenerator(new FileOutputStream(outputFile), JsonEncoding.UTF8);
            gen.useDefaultPrettyPrinter();
            gen.writeStartArray();
            return gen;
        } catch (IOException e) {
            LOG.error("Error creating the output stream for {}", outputFile, e);
            throw new UncheckedIOException(e);
        }
    }

}
