package org.digit.health.registration.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.registration.helper.RegistrationRequestTestBuilder;
import org.digit.health.registration.service.RegistrationService;
import org.digit.health.registration.web.models.RegistrationId;
import org.digit.health.registration.web.models.request.RegistrationDto;
import org.digit.health.registration.web.models.request.RegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationService registrationService;

    @Test
    @DisplayName("should return Http status as 200 and registration id on registration request submission")
    void shouldReturnHttpStatus202AndSyncIdOnSuccessfulRequestSubmission() throws Exception {
        RegistrationRequest registrationRequest = RegistrationRequestTestBuilder.builder().withDetails().build();

        when(registrationService.register(any(RegistrationDto.class))).thenReturn(RegistrationId.builder().registrationId("registration-id").build());

        mockMvc.perform(post("/registration/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationId")
                        .value("registration-id"));
    }
}