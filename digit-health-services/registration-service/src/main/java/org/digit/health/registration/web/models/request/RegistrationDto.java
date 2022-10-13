package org.digit.health.registration.web.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationDto {
    private RequestInfo requestInfo;
    private CampaignData registration;
}
