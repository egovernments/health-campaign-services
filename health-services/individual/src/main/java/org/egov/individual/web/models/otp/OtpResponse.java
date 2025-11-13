package org.egov.individual.web.models.otp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class OtpResponse {

    private ResponseInfo responseInfo;

    @JsonProperty("isSuccessful")
    private boolean successful;
}



