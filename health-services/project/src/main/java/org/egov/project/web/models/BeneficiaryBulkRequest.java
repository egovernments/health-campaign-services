package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * BeneficiaryRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("ProjectBeneficiaries")
    @Valid
    @Size(min=1)
    private List<ProjectBeneficiary> projectBeneficiaries = null;


    public BeneficiaryBulkRequest addProjectBeneficiaryItem(ProjectBeneficiary projectBeneficiaryItem) {
        if (this.projectBeneficiaries == null) {
            this.projectBeneficiaries = new ArrayList<>();
        }
        this.projectBeneficiaries.add(projectBeneficiaryItem);
        return this;
    }

}

