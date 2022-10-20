package org.digit.health.registration.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.registration.service.RegistrationService;
import org.digit.health.registration.utils.ModelMapper;
import org.digit.health.registration.web.models.HouseholdRegistrationRequest;
import org.digit.health.registration.web.models.response.RegistrationResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/registration/v1")
public class RegistrationController {

    private final RegistrationService registrationService;

    @Autowired
    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/_create")
    public ResponseEntity<RegistrationResponse> register(@RequestBody @Valid
                                                             List<HouseholdRegistrationRequest>
                                                                     registrationRequests) {
        log.info("Registration request {}", registrationRequests);
        HouseholdRegistrationRequest registrationRequest = registrationRequests.get(0);
        if (registrationRequest.getHousehold().getClientReferenceId().equals("error")) {
            throw new CustomException("ERROR_IN_REGISTRATION", "Dummy error");
        }
        return ResponseEntity.ok().body(RegistrationResponse.builder()
                .responseInfo(ModelMapper.createResponseInfoFromRequestInfo(registrationRequest
                        .getRequestInfo(), true))
                .registrationId(registrationRequest.getHousehold().getClientReferenceId())
                .build());
    }
}
