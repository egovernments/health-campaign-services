package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
* IndividualSearchRequest
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualSearchRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private org.egov.common.contract.request.RequestInfo requestInfo = null;

        @JsonProperty("Individual")
      @NotNull

  @Valid


    private IndividualSearch individual = null;


}

