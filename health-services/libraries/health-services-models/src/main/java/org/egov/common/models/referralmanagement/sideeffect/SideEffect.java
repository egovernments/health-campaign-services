package org.egov.common.models.referralmanagement.sideeffect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.egov.common.models.project.AdditionalFields;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SideEffect extends EgovOfflineModel {

    @JsonProperty("taskId")
    @Size(min = 2, max = 64)
    private String taskId;

    @JsonProperty("taskClientReferenceId")
    @NotNull
    @Size(min = 2, max = 64)
    private String taskClientReferenceId;

    @JsonProperty("projectBeneficiaryId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryClientReferenceId;

    @JsonProperty("symptoms")
    @NotNull
    @Size(min=1)
    private List<String> symptoms;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}
