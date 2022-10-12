package org.digit.health.delivery.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.delivery.service.DeliveryService;
import org.digit.health.delivery.web.models.request.DeliveryRequest;
import org.digit.health.delivery.web.models.response.DeliveryResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Controller
@Slf4j
@RequestMapping("/delivery/v1")
public class DeliveryController {

    @PostMapping("/_create")
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid DeliveryRequest deliveryRequest) {
        log.info("create delivery request {}", deliveryRequest.toString());
        return ResponseEntity.accepted().body(DeliveryResponse.builder().responseInfo(ResponseInfo.builder().status("delivery created").build()).build());
    }
}
