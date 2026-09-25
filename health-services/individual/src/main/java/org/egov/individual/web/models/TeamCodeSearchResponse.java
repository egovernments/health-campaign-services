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

@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamCodeSearchResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("TeamCodes")
    @Valid
    private List<TeamCode> teamCodes;

    @JsonProperty("TotalCount")
    private Long totalCount;

}
