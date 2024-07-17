package org.egov.common.models.referralmanagement.sideeffect;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;

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
