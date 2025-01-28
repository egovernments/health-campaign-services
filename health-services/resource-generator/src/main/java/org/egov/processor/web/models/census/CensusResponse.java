package org.egov.processor.web.models.census;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * CensusResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CensusResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Census")
    @Valid
    private List<Census> census = null;

    @JsonProperty("TotalCount")
    @Valid
    private Integer totalCount = null;

    @JsonProperty("StatusCount")
    @Valid
    private Map<String, Integer> statusCount = null;

}
