package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaGatewayOtpVerifyResponse {

    @JsonProperty("txnId")
    private String txnId;

    @JsonProperty("tokens")
    private AbhaToken tokens;

    @JsonProperty("ABHAProfile")
    private AbhaProfile abhaProfile;
}
