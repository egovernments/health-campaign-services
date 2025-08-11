package org.egov.common.models.individual;// package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AbhaOtpResendRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    private RequestInfo requestInfo;

    @JsonProperty("individualId")
    @NotBlank
    private String individualId;

    @JsonProperty("aadhaarNumber")
    @NotBlank
    private String aadhaarNumber;
}
