package org.egov.inbox.web.model.V2;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IndexSearchCriteria {
    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;

    @NotNull
    @JsonProperty("moduleName")
    private String moduleName;

    @JsonProperty("moduleSearchCriteria")
    private HashMap<String,Object> moduleSearchCriteria;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    @Max(value = 300)
    private Integer limit;
}
