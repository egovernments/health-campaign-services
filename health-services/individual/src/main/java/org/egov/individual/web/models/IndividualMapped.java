package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.egov.individual.config.ApplicationContextProvider;
import org.egov.individual.config.IndividualProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Validated
@JsonIgnoreProperties(ignoreUnknown = false)
public class IndividualMapped {

    private Set<String> allowedKeys = new HashSet<>();
    private Map<String, Object> fields = new HashMap<>();

    @Value("${individual.allowed-response-fields}")
    private List<String> allowedResponseFields;

    // ✅ Empty constructor that initializes allowedKeys from application.properties
    public IndividualMapped() {
        try {
            IndividualProperties props = ApplicationContextProvider.getBean(IndividualProperties.class);
            List<String> allowedFields = props.getAllowedResponseFields();

            if (allowedFields == null) {
                allowedFields = new ArrayList<>();
            }

            if (!allowedFields.contains("username")) {
                allowedFields.add("username");
            }

            if (!allowedFields.contains("mobilenumber")) {
                allowedFields.add("mobilenumber");
            }

            this.allowedKeys = new HashSet<>(allowedFields);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load IndividualProperties from context", e);
        }
    }

    @JsonAnySetter
    public void setField(String key, Object value) {
        if (!allowedKeys.contains(key)) {
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
