package org.egov.inbox.web.model;

import java.util.HashMap;

import org.egov.inbox.web.model.workflow.ProcessInstanceSearchCriteria;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class InboxSearchCriteria {


    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;

    @Valid
    @JsonProperty("processSearchCriteria")
    private ProcessInstanceSearchCriteria processSearchCriteria;
    
    @JsonProperty("moduleSearchCriteria")
    private HashMap<String,Object> moduleSearchCriteria;
    
    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    @Max(value = 300)
    private Integer limit;
}
