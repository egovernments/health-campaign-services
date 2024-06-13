package org.egov.transformer.models.boundary;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
 * BoundaryRequest
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryRequestCopy {

    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo = null;

}
