package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
* HouseholdRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private org.egov.common.contract.request.RequestInfo requestInfo = null;

        @JsonProperty("Household")
      @NotNull

  @Valid

    @Size(min=1) 

    private List<Household> household = new ArrayList<>();

        @JsonProperty("apiOperation")
    
  @Valid


    private ApiOperation apiOperation = null;


        public HouseholdRequest addHouseholdItem(Household householdItem) {
        this.household.add(householdItem);
        return this;
        }

}

