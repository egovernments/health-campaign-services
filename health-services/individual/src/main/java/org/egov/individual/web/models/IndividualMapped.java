package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Validated
@JsonIgnoreProperties(ignoreUnknown = false)
public class IndividualMapped {

    private final Set<String> allowedKeys = Set.of("useruuid", "mobilenumber", "username");

    private Map<String, Object> fields = new HashMap<>();

    @JsonAnySetter
    public void setField(String key, Object value) {
        if (!allowedKeys.contains(key.toLowerCase())) {
            throw new IllegalArgumentException("Invalid field: " + key + ". Allowed fields are: " + allowedKeys);
        }
        fields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getFields() {
        return fields;
    }

    public Object get(String key) {
        return fields.get(key);
    }
}
