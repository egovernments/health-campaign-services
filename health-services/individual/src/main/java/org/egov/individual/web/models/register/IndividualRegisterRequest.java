package org.egov.individual.web.models.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndividualRegisterRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("IndividualRegister")
    @NotNull
    @Valid
    private IndividualRegister individualRegister;
}
