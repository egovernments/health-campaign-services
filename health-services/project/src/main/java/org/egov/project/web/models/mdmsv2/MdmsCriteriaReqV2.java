package org.egov.project.web.models.mdmsv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MdmsCriteriaReqV2 {
    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("MdmsCriteria")
    @Valid
    private MdmsCriteriaV2 mdmsCriteria = null;
}
