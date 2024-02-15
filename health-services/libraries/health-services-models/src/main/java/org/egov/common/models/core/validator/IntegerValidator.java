package org.egov.common.models.core.validator;

import java.io.IOException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

// Custom JSON deserializer for Integer validation
public class IntegerValidator extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException{
        // Get the JSON value as a string
        String value = jsonParser.getValueAsString();
        // Check if the value matches the pattern for an integer
        if(!Pattern.matches("\\d+",value)){
            // Throw a JsonMappingException if the input value is invalid
            throw new JsonMappingException("Invalid input value");
        }
        // Parse and return the integer value
        return Integer.valueOf(value);
    }
}

