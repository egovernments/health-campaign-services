package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.project.AdditionalFields;

import javax.validation.Valid;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectBeneficiaryIndexV1 {
    @JsonProperty("id")
    private String id = null;
    @JsonProperty("tenantId")
    private String tenantId = null;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("projectId")
    private String projectId = null;
    @JsonProperty("beneficiaryId")
    private String beneficiaryId = null;
    @JsonProperty("dateOfRegistration")
    private Long dateOfRegistration = null;
    @JsonProperty("clientReferenceId")
    private String clientReferenceId = null;
    @JsonProperty("beneficiaryClientReferenceId")
    private String beneficiaryClientReferenceId = null;
    @JsonProperty("additionalFields")
    private @Valid AdditionalFields additionalFields = null;
    @JsonProperty("isDeleted")
    private Boolean isDeleted;
    @JsonProperty("rowVersion")
    private Integer rowVersion;
    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;
    @JsonProperty("clientAuditDetails")
    private @Valid AuditDetails clientAuditDetails;
    @JsonIgnore
    private Boolean hasErrors;
    @JsonProperty("tag")
    private String tag;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;


}
