package org.digit.health.delivery.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.delivery.utils.ModelMapper;
import org.digit.health.delivery.web.models.request.ResourceDeliveryRequest;
import org.digit.health.delivery.web.models.response.DeliveryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@Controller
@Slf4j
@RequestMapping("/delivery/v1")
public class DeliveryController {

    @PostMapping("/_create")
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid ResourceDeliveryRequest
                                                               deliveryRequest) {
        log.info("Delivery request {}", deliveryRequest);
        return ResponseEntity.ok().body(DeliveryResponse.builder()
                .responseInfo(ModelMapper
                        .createResponseInfoFromRequestInfo(deliveryRequest
                                .getRequestInfo(), true))
                .deliveryId(deliveryRequest.getDelivery().getDeliveryId()).build());
    }
}
