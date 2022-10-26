package org.digit.health.delivery.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.delivery.web.models.request.BulkDeliveryRequest;
import org.digit.health.delivery.web.models.request.DeliveryRequest;
import org.digit.health.delivery.web.models.response.DeliveryResponse;
import org.egov.common.contract.response.ResponseInfo;
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
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid DeliveryRequest deliveryRequest) {
        log.info("create delivery request {}", deliveryRequest.toString());
        return ResponseEntity.ok().body(DeliveryResponse.builder().responseInfo(ResponseInfo.builder().status("delivery created").build()).build());
    }

    @PostMapping("/_create/bulk")
    public ResponseEntity<DeliveryResponse> createBulk(@RequestBody @Valid BulkDeliveryRequest bulkDeliveryRequest) {
        log.info("create bulk delivery request {}", bulkDeliveryRequest.toString());
        return ResponseEntity.ok().body(DeliveryResponse.builder().responseInfo(ResponseInfo.builder().status("delivery created").build()).build());
    }
}
