package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* IndividualResponse
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndividualResponse   {
        @JsonProperty("ResponseInfo")
      @NotNull

  @Valid


    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

        @JsonProperty("Individual")
    
  @Valid


    private List<Individual> individual = null;


        public IndividualResponse addIndividualItem(Individual individualItem) {
            if (this.individual == null) {
            this.individual = new ArrayList<>();
            }
        this.individual.add(individualItem);
        return this;
        }

}

