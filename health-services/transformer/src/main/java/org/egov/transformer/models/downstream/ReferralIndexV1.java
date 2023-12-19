package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferralIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("clientReferenceId")
    private String clientReferenceId;
    @JsonProperty("projectBeneficiaryId")
    private String projectBeneficiaryId;
    @JsonProperty("projectBeneficiaryClientReferenceId")
    private String projectBeneficiaryClientReferenceId;
    @JsonProperty("referrerId")
    private String referrerId;
    @JsonProperty("recipientType")
    private String recipientType;
    @JsonProperty("recipientId")
    private String recipientId;
    @JsonProperty("reasons")
    private List<String> reasons;
    @JsonProperty("clientLastModifiedTime")
    private Long clientLastModifiedTime;
}