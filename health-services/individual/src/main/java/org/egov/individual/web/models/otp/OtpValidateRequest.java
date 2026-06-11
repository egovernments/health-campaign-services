package org.egov.individual.web.models.otp;

import org.egov.common.contract.request.RequestInfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.egov.individual.web.models.otp.OtpValidate;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class OtpValidateRequest {
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;
    private OtpValidate otp;
}

