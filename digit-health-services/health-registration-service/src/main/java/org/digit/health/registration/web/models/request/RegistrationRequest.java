package org.digit.health.registration.web.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Validated
public class RegistrationRequest {
    @JsonProperty("requestInfo")
    private RequestInfo requestInfo;

    @Valid
    @JsonProperty("registration")
    private CampaignData registration;

}
