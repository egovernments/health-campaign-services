package org.digit.health.delivery.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.delivery.web.models.Delivery;
import org.digit.health.delivery.web.models.request.ResourceDeliveryRequest;
import org.egov.common.contract.request.RequestInfo;
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
    @DisplayName("should return Http status as 200 when the delivery request is valid")
    void shouldReturnHttpStatus202WhenRequestIsValid() throws Exception {
        ResourceDeliveryRequest request = ResourceDeliveryRequest.builder()
                .delivery(Delivery.builder()
                        .deliveryId("some-delivery-id")
                        .build())
                .requestInfo(RequestInfo.builder().build())
                .build();
        String content = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/delivery/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());
    }
}