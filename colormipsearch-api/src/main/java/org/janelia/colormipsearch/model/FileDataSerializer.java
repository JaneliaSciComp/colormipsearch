package org.janelia.colormipsearch.model;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class FileDataSerializer extends JsonSerializer<FileData> {
    @Override
    public void serialize(FileData value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            serializers.defaultSerializeNull(gen);
        } else if (value.getDataType() == FileData.FileDataType.zipEntry) {
            gen.writeStartObject();
            gen.writeStringField("dataType", value.getDataType().toString());
            gen.writeStringField("fileName", value.getFileName());
            gen.writeStringField("entryName", value.getEntryName());
            gen.writeEndObject();
        } else {
            gen.writeString(value.getFileName());
        }
    }
}
