package org.egov.common.models.referralmanagement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral {

    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("projectBeneficiaryId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryId = null;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryClientReferenceId = null;

    @JsonProperty("referrerId")
    @Size(min = 2, max = 64)
    private String referrerId = null;

    @JsonProperty("recipientType")
    private String recipientType = null;

    @JsonProperty("recipientId")
    @Size(min = 2, max = 64)
    private String recipientId = null;

    @JsonProperty("reasons")
    @NotNull
    @Size(min=1)
    private List<String> reasons = null;

    @JsonProperty("sideEffect")
    private SideEffect sideEffect = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2, max = 1000)
    private String tenantId = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("clientAuditDetails")
    @Valid
    private AuditDetails clientAuditDetails = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;
}
