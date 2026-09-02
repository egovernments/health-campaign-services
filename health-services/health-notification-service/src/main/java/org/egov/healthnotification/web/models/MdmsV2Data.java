package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MDMS v2 data object containing the actual configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdmsV2Data {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("schemaCode")
    private String schemaCode;

    @JsonProperty("uniqueIdentifier")
    private String uniqueIdentifier;

    @JsonProperty("data")
    private JsonNode data;

    @JsonProperty("isActive")
    private Boolean isActive;
}
