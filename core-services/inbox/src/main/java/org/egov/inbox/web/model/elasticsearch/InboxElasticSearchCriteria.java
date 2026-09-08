package org.egov.inbox.web.model.elasticsearch;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



@Data
public class InboxElasticSearchCriteria {

    @NotNull
    @JsonProperty("indexKey")
    private String indexKey;

    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("fromDate")
    private long fromDate;

    @JsonProperty("toDate")
    private long toDate;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    @Max(value = 300)
    private Integer limit;

    @JsonProperty("sortOrder")
    private String sortOrder;

}
