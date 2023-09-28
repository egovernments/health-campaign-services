package org.egov.common.models.referralmanagement.sideeffect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SideEffectBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("SideEffects")
    @NotNull
    @Valid
    @Size(min=1)
    private List<SideEffect> sideEffects = new ArrayList<>();

    public SideEffectBulkRequest addSideEffectItem(SideEffect sideEffectItem) {
        this.sideEffects.add(sideEffectItem);
        return this;
    }

}
