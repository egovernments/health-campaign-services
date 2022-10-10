package org.digit.health.registration.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.registration.service.RegistrationService;
import org.digit.health.registration.utils.ModelMapper;
import org.digit.health.registration.web.models.RegistrationId;
import org.digit.health.registration.web.models.request.RegistrationRequest;
import org.digit.health.registration.web.models.request.RegistrationMapper;
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
    public ResponseEntity<RegistrationResponse> syncUp(@RequestBody @Valid RegistrationRequest registrationRequest) {
        log.info("Registration request {}", registrationRequest.toString());
        RegistrationId registrationId = registrationService.register(RegistrationMapper.INSTANCE.toDTO(registrationRequest));
        return ResponseEntity.accepted().body(RegistrationResponse.builder()
                .responseInfo(ModelMapper.createResponseInfoFromRequestInfo(registrationRequest
                        .getRequestInfo(), true))
                .registrationId(registrationId.getRegistrationId())
                .build());
    }
}
