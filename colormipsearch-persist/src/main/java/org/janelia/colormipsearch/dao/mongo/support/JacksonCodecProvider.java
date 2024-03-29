package org.janelia.colormipsearch.dao.mongo.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

public class JacksonCodecProvider implements CodecProvider {

    private final ObjectMapper objectMapper;

    JacksonCodecProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (checkCodecApplicability(clazz)) {
            final Codec<Document> rawBsonDocumentCodec = registry.get(Document.class);
            return new Codec<T>() {
                @Override
                public T decode(BsonReader reader, DecoderContext decoderContext) {
                    try {
                        Document document = rawBsonDocumentCodec.decode(reader, decoderContext);
                        return objectMapper.readValue(document.toJson(), clazz);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
                    try {
                        String json = objectMapper.writeValueAsString(value);
                        rawBsonDocumentCodec.encode(writer, Document.parse(json), encoderContext);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Error encoding " + value, e);
                    }
                }

                @Override
                public Class<T> getEncoderClass() {
                    return clazz;
                }
            };
        }
        return null;
    }

    private <T> boolean checkCodecApplicability(Class<T> clazz) {
        return true;
    }
}
