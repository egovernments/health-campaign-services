package org.digit.health.registration.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.registration.service.RegistrationService;
import org.digit.health.registration.utils.ModelMapper;
import org.digit.health.registration.web.models.HouseholdRegistrationRequest;
import org.digit.health.registration.web.models.response.RegistrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

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
                                                             HouseholdRegistrationRequest
                                                                     registrationRequest) {
        log.info("Registration request {}", registrationRequest);
        return ResponseEntity.ok().body(RegistrationResponse.builder()
                .responseInfo(ModelMapper.createResponseInfoFromRequestInfo(registrationRequest
                        .getRequestInfo(), true))
                .registrationId(registrationRequest.getHousehold().getClientReferenceId())
                .build());
    }
}
