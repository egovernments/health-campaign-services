package org.egov.common.models.core.validator;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes receiverId (and similar fields) from either:
 * - A comma-separated string (e.g. "id1,id2,id3") into List of trimmed strings
 * - A JSON array (e.g. ["id1", "id2"]) as-is
 */
public class CommaSeparatedStringToListDeserializer extends StdDeserializer<List<String>> {

    public CommaSeparatedStringToListDeserializer() {
        this(null);
    }

    public CommaSeparatedStringToListDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> list = new java.util.ArrayList<>();
            node.forEach(elem -> list.add(elem.isNull() ? null : elem.asText().trim()));
            return list;
        }
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty()) {
                return null;
            }
            return Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.toList());
        }
        return null;
    }
}
