package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Campaign {

	@JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;
	
	@JsonProperty("id")
    private String id;
	
	@JsonProperty("status")
    @Valid
    private String status;
	
	@JsonProperty("action")
	@Size(min = 1, max = 64)
    private String action;

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("parentId")
    private String parentId;

	@JsonProperty("campaignNumber")
    @Valid
    private String campaignNumber;
	
	@JsonProperty("campaignName")
	@Size(min = 2, max = 250)
    private String campaignName;
	
	@JsonProperty("projectType")
	@Size(min = 1, max = 128)
    private String projectType;
	
	@JsonProperty("hierarchyType")
	@Size(min = 1, max = 128)
    private String hierarchyType;
	
	@JsonProperty("boundaryCode")
    @Valid
    private String boundaryCode;
	
	@JsonProperty("projectId")
    @Valid
    private String projectId;
	
	@JsonProperty("startDate")
    private long startDate;
	
	@JsonProperty("endDate")
    private long endDate;
	
	@JsonProperty("additionalDetails")
    @Valid
    private AdditionalDetails additionalDetails;
	
	@JsonProperty("resources")
    @Valid
    private List<CampaignResources> resources;
	
    @JsonProperty("boundaries")
    @Valid
    private Boundary[] boundaries = new Boundary[0];
	
	@JsonProperty("deliveryRules")
    @Valid
    private DeliveryRule[] deliveryRules= new DeliveryRule[0];
	
	@JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
	
	
}
