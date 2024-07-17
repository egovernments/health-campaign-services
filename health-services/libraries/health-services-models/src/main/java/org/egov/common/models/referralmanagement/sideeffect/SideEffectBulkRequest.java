package org.egov.common.models.referralmanagement.sideeffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SideEffectBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("SideEffects")
    @NotNull
    @Valid
    @Size(min=1)
    private List<SideEffect> sideEffects = new ArrayList<>();

    /**
     * Add a SideEffect item to the list of side effects in the request.
     *
     * @param sideEffectItem The SideEffect item to add to the request.
     * @return The updated SideEffectBulkRequest.
     */
    public SideEffectBulkRequest addSideEffectItem(SideEffect sideEffectItem) {
        if(Objects.isNull(sideEffects))
            sideEffects = new ArrayList<>();
        if(Objects.nonNull(sideEffectItem))
            this.sideEffects.add(sideEffectItem);
        return this;
    }

}
