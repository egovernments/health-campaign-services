package org.egov.common.models.core.validator;

import java.io.IOException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

public class IntegerValidator extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        String value = jsonParser.getText();
        if(!Pattern.matches("\\d+",value)){
            throw new JsonMappingException("Quantity value must be an Integer");
        }
        return Integer.valueOf(value);
    }
}

