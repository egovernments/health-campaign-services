package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Criteria for MDMS v2 search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdmsV2Criteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("schemaCode")
    private String schemaCode;

    @JsonProperty("filters")
    private Map<String, String> filters;

    @JsonProperty("limit")
    @Builder.Default
    private Integer limit = 100;

    @JsonProperty("offset")
    @Builder.Default
    private Integer offset = 0;
}
