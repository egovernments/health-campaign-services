package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaOtpResponse {

    @JsonProperty("txnId")
    private String txnId;

    @JsonProperty("message")
    private String message;
}
