package org.digit.health.registration.web.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.registration.web.models.HouseholdRegistration;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Validated
public class BulkRegistrationRequest {
    @JsonProperty("requestInfo")
    private RequestInfo requestInfo;

    @Valid
    @JsonProperty("registrations")
    private List<HouseholdRegistration> registration;

}
