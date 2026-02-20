package org.egov.fhir.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MappingConfig {
    private String version;
    private String fhirResource;
    private String egovModel;
    private String egovService;
    private Map<String, ApiMapping> apiMapping;
    private Map<String, ModelDef> requestModels;
    private Map<String, ModelDef> responseModels;
    private List<FieldMapping> fieldMappings;
    private List<IdentifierMapping> identifierMappings;
    private List<FieldMapping> searchParamMappings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiMapping {
        private String endpoint;
        private String method;
        private String requestModel;
        private String responseModel;
        private Map<String, String> headers;
        private Boolean softDelete;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelDef {
        private String basePath;
        private Boolean isArray;
        private String totalCountPath;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldMapping {
        private String fhirField;
        private String fhirParam;  // for search param mappings
        private String egovField;
        private String transform;
        private Map<String, Object> transformConfig;
        private Boolean isArray;
        private List<FieldMapping> fieldMappings; // nested for complex types

        // Return fhirParam if set, otherwise fhirField (for search mappings)
        public String getFhirKey() {
            return fhirParam != null ? fhirParam : fhirField;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IdentifierMapping {
        private String name;
        private String system;
        private String egovField;
        private String use;
        private Boolean isBusinessId;
        private Map<String, Object> privacy;
    }
}
