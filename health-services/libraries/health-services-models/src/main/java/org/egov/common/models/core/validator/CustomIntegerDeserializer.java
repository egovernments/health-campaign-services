package org.egov.common.models.core.validator;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.egov.tracer.model.CustomException;

// Custom deserializer for Integer values
public class CustomIntegerDeserializer extends StdDeserializer<Integer> {

    public CustomIntegerDeserializer() {
        this(null);
    }

    public CustomIntegerDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

        // Read the JSON tree from the parser
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        System.out.println(node.toString());
        if(node.asLong() > Integer.MAX_VALUE){
            throw new CustomException("INVALID_INPUT","Value must be an Integer");
        }

        // Parse the quantity as an integer
        int quantity = node.asInt();

        // Check if the parsed quantity matches the original string representation
        if ((double) quantity != Double.parseDouble(node.asText())) {
            throw new CustomException("INVALID_INPUT", "Quantity must be an integer");
        }

        // Return the parsed quantity
        return quantity;
    }
}
