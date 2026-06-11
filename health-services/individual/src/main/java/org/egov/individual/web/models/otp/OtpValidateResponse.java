package org.egov.individual.web.models.otp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OtpValidateResponse {
    private OtpInValidateResponse otp;
    private ResponseInfo responseInfo;

    public boolean isValidationComplete(String mobileNumber) {
        return otp != null && otp.isValidationComplete(mobileNumber);
    }

}
