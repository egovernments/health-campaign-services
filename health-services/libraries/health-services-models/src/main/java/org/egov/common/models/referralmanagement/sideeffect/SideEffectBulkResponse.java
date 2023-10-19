package org.egov.common.models.referralmanagement.sideeffect;

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
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SideEffectBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("SideEffects")
    @NotNull
    @Valid
    private List<SideEffect> sideEffects = new ArrayList<>();

    public SideEffectBulkResponse addSideEffectItem(SideEffect sideEffectItem) {
        if(Objects.nonNull(sideEffectItem))
            this.sideEffects.add(sideEffectItem);
        return this;
    }
}
