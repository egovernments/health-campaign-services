package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaGatewayOtpVerifyRequest {

    @JsonProperty("txnId")
    @NotNull
    private String txnId;

    @JsonProperty("Otp")
    @NotNull
    private String otp;

    @JsonProperty("mobile")
    @NotNull
    private String mobile;
}
