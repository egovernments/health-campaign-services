package org.egov.individual.web.models;

import java.util.List;
import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * TeamCodeGenerationResponse
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamCodeGenerationResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("teamCodes")
    private List<String> teamCodes;

}
