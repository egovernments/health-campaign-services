package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchAbhaNumberRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    private RequestInfo requestInfo;

    @JsonProperty("individualId")
    @NotBlank
    private String individualId;

    @JsonProperty("tenantId")
    @NotBlank
    private String tenantId;
}
