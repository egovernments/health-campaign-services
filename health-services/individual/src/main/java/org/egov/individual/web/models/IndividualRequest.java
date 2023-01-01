package org.egov.individual.web.models;

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
* IndividualRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndividualRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private org.egov.common.contract.request.RequestInfo requestInfo = null;

        @JsonProperty("Individual")
      @NotNull

  @Valid

    @Size(min=1) 

    private List<Individual> individual = new ArrayList<>();

        @JsonProperty("apiOperation")
    
  @Valid


    private ApiOperation apiOperation = null;


        public IndividualRequest addIndividualItem(Individual individualItem) {
        this.individual.add(individualItem);
        return this;
        }

}

