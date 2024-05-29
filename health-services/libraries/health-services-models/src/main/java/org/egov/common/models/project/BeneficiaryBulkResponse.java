package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * BeneficiaryResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeneficiaryBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("ProjectBeneficiaries")
    @Valid
    private List<ProjectBeneficiary> projectBeneficiaries = null;


    public BeneficiaryBulkResponse addProjectBeneficiaryItem(ProjectBeneficiary projectBeneficiaryItem) {
        if (this.projectBeneficiaries == null) {
            this.projectBeneficiaries = new ArrayList<>();
        }
        this.projectBeneficiaries.add(projectBeneficiaryItem);
        return this;
    }

}

