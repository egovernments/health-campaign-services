package org.digit.health.delivery.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.delivery.helper.DeliveryRequestTestBuilder;
import org.digit.health.delivery.web.models.request.DeliveryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = DeliveryController.class)
class DeliveryControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("should return Http status as 202 when the delivery request is valid")
    void shouldReturnHttpStatus202WhenRequestIsValid() throws Exception {
        DeliveryRequest deliveryRequest = DeliveryRequestTestBuilder.builder().withDeliveryRequest().build();
        String content = objectMapper.writeValueAsString(deliveryRequest);

        mockMvc.perform(post("/delivery/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isAccepted());
    }
}