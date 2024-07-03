package org.janelia.colormipsearch.model.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.janelia.colormipsearch.model.FileData;

public class FileDataDeserializer extends JsonDeserializer<FileData> {
    @Override
    public FileData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
            return null;
        } else if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
            return FileData.fromString(p.getValueAsString());
        } else if (p.getCurrentToken() == JsonToken.START_OBJECT) {
            JsonNode n = p.readValueAsTree();
            JsonNode dataTypeNode = n.findValue("dataType");
            JsonNode fileNameNode = n.findValue("fileName");
            JsonNode entryNameNode = n.findValue("entryName");
            return FileData.fromComponents(
                    dataTypeNode != null ? FileData.FileDataType.valueOf(dataTypeNode.asText()) : FileData.FileDataType.file,
                    fileNameNode != null ? fileNameNode.asText() : null,
                    entryNameNode != null ? entryNameNode.asText() : null
            );
        } else {
            return null;
        }
    }
}
