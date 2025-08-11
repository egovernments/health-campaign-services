package org.egov.common.models.individual;// package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbhaOtpResendResponse {
    @JsonProperty("responseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("transactionId")
    private String transactionId;
}
