package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerationSearchCriteria {

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("referenceIds")
    private List<String> referenceIds;

    @JsonProperty("tenantId")
    @NotBlank(message = "INGEST_MISSING_TENANT_ID")
    private String tenantId;

    @JsonProperty("types")
    private List<String> types;

    @JsonProperty("statuses")
    private List<String> statuses;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;
}