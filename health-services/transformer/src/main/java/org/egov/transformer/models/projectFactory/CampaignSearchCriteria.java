package org.egov.transformer.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CampaignSearchCriteria {
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("isActive")
    private Boolean isActive;
    @JsonProperty("campaignNumber")
    private String campaignNumber;
    @JsonProperty("ids")
    private List<String> ids;
    @JsonProperty("startDate")
    private Long startDate;
    @JsonProperty("endDate")
    private Long endDate;
    @JsonProperty("campaignName")
    private String campaignName;
    @JsonProperty("projectType")
    private String projectType;
    @JsonProperty("status")
    private Object status;
}
