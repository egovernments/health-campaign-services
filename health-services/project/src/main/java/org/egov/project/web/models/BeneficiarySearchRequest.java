package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
* BeneficiarySearchRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeneficiarySearchRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private RequestInfo requestInfo = null;

        @JsonProperty("ProjectBeneficiary")
    
  @Valid


    private ProjectBeneficiarySearch projectBeneficiary = null;


}

