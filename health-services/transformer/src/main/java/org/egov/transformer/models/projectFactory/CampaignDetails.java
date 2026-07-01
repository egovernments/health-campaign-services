package org.egov.transformer.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CampaignDetails {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private Object status;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("campaignName")
    private String campaignName;

    @JsonProperty("campaignNumber")
    private String campaignNumber;

    @JsonProperty("action")
    private String action;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("parentId")
    private String parentId;

    @JsonProperty("startDate")
    private Long startDate;

    @JsonProperty("endDate")
    private Long endDate;

    @JsonProperty("boundaries")
    private List<Boundary> boundaries;

    @JsonProperty("resources")
    private List<Resource> resources;

    @JsonProperty("projectType")
    private String projectType;

    @JsonProperty("deliveryRules")
    private List<Object> deliveryRules;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
