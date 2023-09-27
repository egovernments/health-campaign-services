<<<<<<<< HEAD:health-services/libraries/health-services-models/src/main/java/org/egov/common/models/referralmanagement/sideeffect/SideEffectSearchRequest.java
package org.egov.common.models.referralmanagement.sideeffect;
========
package org.egov.common.models.referralmanagement.adverseevent;
>>>>>>>> 51cd6f6468 (HLM-3069: changed module name to referral management):health-services/libraries/health-services-models/src/main/java/org/egov/common/models/referralmanagement/adverseevent/AdverseEventSearchRequest.java

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SideEffectSearchRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("SideEffect")
    @Valid
    private SideEffectSearch sideEffect = null;
}
