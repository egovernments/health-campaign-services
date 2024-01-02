package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferralIndexV1 {
    @JsonProperty("id")
    private String id;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("projectBeneficiaryId")
    private String projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    private String projectBeneficiaryClientReferenceId;

    @JsonProperty("dateOfBirth")
    private Long dateOfBirth;

    @JsonProperty("age")
    private Integer age;

    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;

    @JsonProperty("referrerId")
    private String referrerId;

    @JsonProperty("recipientType")
    private String recipientType;

    @JsonProperty("recipientId")
    private String recipientId;

    @JsonProperty("reasons")
    private List<String> reasons;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("clientAuditDetails")
    private AuditDetails clientAuditDetails;
}
