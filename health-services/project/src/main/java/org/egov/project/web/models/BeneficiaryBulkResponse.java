package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* BeneficiaryResponse
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryBulkResponse {
        @JsonProperty("ResponseInfo")
      @NotNull

  @Valid


    private ResponseInfo responseInfo = null;

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

