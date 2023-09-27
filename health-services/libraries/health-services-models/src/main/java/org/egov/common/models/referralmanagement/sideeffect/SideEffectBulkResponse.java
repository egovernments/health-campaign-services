<<<<<<<< HEAD:health-services/libraries/health-services-models/src/main/java/org/egov/common/models/referralmanagement/sideeffect/SideEffectBulkResponse.java
package org.egov.common.models.referralmanagement.sideeffect;
========
package org.egov.common.models.referralmanagement.adverseevent;
>>>>>>>> 51cd6f6468 (HLM-3069: changed module name to referral management):health-services/libraries/health-services-models/src/main/java/org/egov/common/models/referralmanagement/adverseevent/AdverseEventBulkResponse.java

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SideEffectBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("SideEffects")
    @NotNull
    @Valid
    private List<SideEffect> sideEffects = new ArrayList<>();

    public SideEffectBulkResponse addSideEffectItem(SideEffect sideEffectItem) {
        this.sideEffects.add(sideEffectItem);
        return this;
    }
}
