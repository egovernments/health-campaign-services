package digit.web.models.projectFactory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;

import java.util.List;

/**
 * CampaignDetails
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CampaignDetail {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("action")
    private String action;

    @JsonProperty("campaignNumber")
    private String campaignNumber;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("parentId")
    private String parentId;

    @JsonProperty("campaignName")
    private String campaignName;

    @JsonProperty("projectType")
    private String projectType;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("startDate")
    private Long startDate;

    @JsonProperty("endDate")
    private Long endDate;

    @JsonProperty("additionalDetails")
    @Valid
    private Object additionalDetails;

    @JsonProperty("resources")
    @Valid
    private List<Resource> resources;

    @JsonProperty("boundaries")
    @Valid
    private List<Boundary> boundaries;

    @JsonProperty("deliveryRules")
    @Valid
    private List<DeliveryRule> deliveryRules;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
}


