package org.egov.individual.web.models.otp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.user.enums.UserType;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Setter
public class OtpValidate {
    private String otp;
    @JsonProperty("UUID")

    private String uuid;
    private String identity;
    private String tenantId;
    private UserType userType;

    @JsonProperty("isValidationSuccessful")
    private boolean validationSuccessful;
}
