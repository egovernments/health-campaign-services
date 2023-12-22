package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.ProjectBeneficiary;

import javax.validation.Valid;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectTaskIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("taskId")
    private String taskId;
    @JsonProperty("taskType")
    private String taskType;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("startDate")
    private Long startDate;
    @JsonProperty("endDate")
    private Long endDate;
    @JsonProperty("productVariant")
    private String productVariant;
    @JsonProperty("productName")
    private String productName;
    @JsonProperty("quantity")
    private Double quantity;
    @JsonProperty("doseNumber")
    private Integer doseNumber;
    @JsonProperty("cycleNumber")
    private Integer cycleNumber;
    @JsonProperty("quantityWasted")
    private Integer quantityWasted;
    @JsonProperty("deliveryStrategy")
    private String deliveryStrategy;
    @JsonProperty("deliveredTo")
    private String deliveredTo;
    @JsonProperty("isDelivered")
    private boolean isDelivered;
    @JsonProperty("deliveryComments")
    private String deliveryComments;
    @JsonProperty("administrationStatus")
    private String administrationStatus;
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("projectType")
    private String projectType;
    @JsonProperty("latitude")
    private Double latitude;
    @JsonProperty("longitude")
    private Double longitude;
    @JsonProperty("locationAccuracy")
    private Double locationAccuracy;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;
    @JsonProperty("isDeleted")
    private boolean isDeleted;
    @JsonProperty("projectBeneficiaryClientReferenceId")
    private String projectBeneficiaryClientReferenceId;
    @JsonProperty("memberCount")
    private Integer memberCount;
    @JsonProperty("projectBeneficiary")
    private ProjectBeneficiary projectBeneficiary;
    @JsonProperty("household")
    private Household household;
    @JsonProperty("clientReferenceId")
    private String clientReferenceId;
    @JsonProperty("clientAuditDetails")
    private @Valid AuditDetails clientAuditDetails;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("syncedTime")
    private Long syncedTime;
    @JsonProperty("additionalFields")
    private AdditionalFields additionalFields;
    @JsonProperty("geoPoint")
    private List<Double> geoPoint;

}
