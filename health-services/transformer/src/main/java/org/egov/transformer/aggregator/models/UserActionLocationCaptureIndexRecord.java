package org.egov.transformer.aggregator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionLocationCaptureIndexRecord {
    @JsonProperty("id")
    private String id;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("projectId")
    private String projectId;

    // Replacing latitude, longitude, and locationAccuracy with geoJson
    @JsonProperty("geoJson")
    private JsonNode geoJson;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("action")
    private String action;

//    @JsonProperty("createdBy")
//    private String createdBy;
//
//    @JsonProperty("createdTime")
//    private Long createdTime;
//
//    @JsonProperty("lastModifiedBy")
//    private String lastModifiedBy;
//
//    @JsonProperty("lastModifiedTime")
//    private Long lastModifiedTime;

    @JsonProperty("clientCreatedDate")
    private String clientCreatedDate;

//    @JsonProperty("clientLastModifiedTime")
//    private Long clientLastModifiedTime;

    @JsonProperty("clientCreatedBy")
    private String clientCreatedBy;

//    @JsonProperty("clientLastModifiedBy")
//    private String clientLastModifiedBy;

}
