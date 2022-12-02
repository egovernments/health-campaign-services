package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

/**
* BeneficiaryResponse
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryResponse   {
        @JsonProperty("ResponseInfo")
      @NotNull

  @Valid


    private ResponseInfo responseInfo = null;

        @JsonProperty("ProjectBeneficiary")
    
  @Valid


    private List<ProjectBeneficiary> projectBeneficiary = null;


        public BeneficiaryResponse addProjectBeneficiaryItem(ProjectBeneficiary projectBeneficiaryItem) {
            if (this.projectBeneficiary == null) {
            this.projectBeneficiary = new ArrayList<>();
            }
        this.projectBeneficiary.add(projectBeneficiaryItem);
        return this;
        }

}

