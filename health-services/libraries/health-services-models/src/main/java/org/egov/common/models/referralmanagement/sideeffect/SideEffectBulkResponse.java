package org.egov.common.models.referralmanagement.sideeffect;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("SideEffects")
    @NotNull
    @Valid
    private List<SideEffect> sideEffects = new ArrayList<>();

    /**
     * Add a SideEffect item to the list of side effects in the response.
     *
     * @param sideEffectItem The SideEffect item to add to the response.
     * @return The updated SideEffectBulkResponse.
     */
    public SideEffectBulkResponse addSideEffectItem(SideEffect sideEffectItem) {
        if(Objects.isNull(sideEffects))
            sideEffects = new ArrayList<>();
        if(Objects.nonNull(sideEffectItem))
            this.sideEffects.add(sideEffectItem);
        return this;
    }
}
