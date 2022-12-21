package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
* HouseholdSearchRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdSearchRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private org.egov.common.contract.request.RequestInfo requestInfo = null;

        @JsonProperty("Household")
      @NotNull

  @Valid


    private HouseholdSearch household = null;


}

